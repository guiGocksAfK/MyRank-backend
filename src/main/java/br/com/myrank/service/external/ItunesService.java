package br.com.myrank.service.external;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.com.myrank.service.external.MusicCatalogSupport.*;

@Service
public class ItunesService {

    private static final String BASE_URL = "https://itunes.apple.com";
    static final String ID_PREFIX = "itunes:";

    private final RestTemplate restTemplate;
    private final MusicSearchCache cache;

    public ItunesService(RestTemplate restTemplate, MusicSearchCache cache) {
        this.restTemplate = restTemplate;
        this.cache = cache;
    }

    public List<ExternalSearchResultDTO> searchMusic(String query) {
        return search(query, false);
    }

    public List<ExternalSearchResultDTO> searchAlbums(String query) {
        return search(query, true);
    }

    public ExternalWorkDetailsDTO getMusicDetails(String itunesId) {
        return details(id(itunesId), false);
    }

    public ExternalWorkDetailsDTO getAlbumDetails(String itunesId) {
        return details(id(itunesId), true);
    }

    ExternalWorkDetailsDTO findDetails(String title, String artist, boolean album) {
        // IDs da Deezer e do iTunes não são intercambiáveis: a reserva confere título e artista.
        for (JsonNode item : searchResponse(title + " " + artist, album).path("results")) {
            if (matchesType(item, album) && title.equalsIgnoreCase(text(item, titleField(album)))
                    && artist.equalsIgnoreCase(text(item, "artistName"))) {
                return details(item.path(idField(album)).asText(), album);
            }
        }
        throw new RestClientException("Obra não encontrada na reserva do iTunes.");
    }

    private List<ExternalSearchResultDTO> search(String query, boolean album) {
        if (query == null || query.isBlank()) return List.of();
        List<ExternalSearchResultDTO> results = new ArrayList<>();
        for (JsonNode item : searchResponse(query.strip(), album).path("results")) {
            if (!matchesType(item, album)) continue;
            results.add(new ExternalSearchResultDTO(
                    ID_PREFIX + item.path(idField(album)).asText(), text(item, titleField(album)),
                    artwork(item), date(text(item, "releaseDate")), text(item, "artistName")));
        }
        return results;
    }

    private JsonNode searchResponse(String query, boolean album) {
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/search")
                .queryParam("term", "{query}")
                .queryParam("media", "music")
                .queryParam("entity", album ? "album" : "song")
                .queryParam("country", "BR")
                .queryParam("limit", 20)
                .encode().buildAndExpand(query).toUri();
        return get(restTemplate, cache, uri, response -> response.path("results").isArray());
    }

    private ExternalWorkDetailsDTO details(String itunesId, boolean album) {
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/lookup")
                .queryParam("id", itunesId)
                .queryParam("entity", "song")
                .queryParam("country", "BR")
                .queryParam("limit", 200)
                .build().toUri();
        try {
            JsonNode response = get(restTemplate, cache, uri, body -> findItem(body, itunesId, album) != null);
            JsonNode selected = findItem(response, itunesId, album);
            long duration = 0;
            for (JsonNode item : response.path("results")) {
                if (album && "song".equals(text(item, "kind"))
                        && itunesId.equals(item.path("collectionId").asText())) {
                    duration += Math.max(0, item.path("trackTimeMillis").asLong(0));
                }
            }
            if (!album) duration = selected.path("trackTimeMillis").asLong(0);
            ExternalWorkDetailsDTO dto = new ExternalWorkDetailsDTO(text(selected, titleField(album)), artwork(selected),
                    text(selected, "artistName"), date(text(selected, "releaseDate")), minutes(duration, 60_000));
            Map<String, Object> details = new LinkedHashMap<>();
            if (album && selected.path("trackCount").asInt(0) > 0) {
                details.put("trackCount", selected.path("trackCount").asInt());
            } else if (!album && text(selected, "collectionName") != null) {
                details.put("album", text(selected, "collectionName"));
            }
            dto.setDetails(details);
            return dto;
        } catch (RestClientException e) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes no iTunes. Tente novamente em instantes.", e);
        }
    }

    private JsonNode findItem(JsonNode response, String itunesId, boolean album) {
        if (!response.path("results").isArray()) return null;
        for (JsonNode item : response.path("results")) {
            if (matchesType(item, album) && itunesId.equals(item.path(idField(album)).asText())) return item;
        }
        return null;
    }

    private boolean matchesType(JsonNode item, boolean album) {
        return (album ? "collection".equals(text(item, "wrapperType"))
                && "Album".equals(text(item, "collectionType")) : "song".equals(text(item, "kind")))
                && item.path(idField(album)).asLong(0) > 0 && text(item, titleField(album)) != null;
    }

    /** O iTunes devolve capas de 100px; a mesma URL aceita pedir 600px, que não fica borrada no card. */
    private String artwork(JsonNode item) {
        String url = first(text(item, "artworkUrl100"), text(item, "artworkUrl60"), text(item, "artworkUrl30"));
        return url == null ? null : url.replaceFirst("/\\d+x\\d+bb\\.", "/600x600bb.");
    }

    private String titleField(boolean album) {
        return album ? "collectionName" : "trackName";
    }

    private String idField(boolean album) {
        return album ? "collectionId" : "trackId";
    }
}
