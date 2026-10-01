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
public class DeezerService {

    private static final String BASE_URL = "https://api.deezer.com";

    private final RestTemplate restTemplate;
    private final ItunesService itunesService;
    private final MusicSearchCache cache;

    public DeezerService(RestTemplate restTemplate, ItunesService itunesService, MusicSearchCache cache) {
        this.restTemplate = restTemplate;
        this.itunesService = itunesService;
        this.cache = cache;
    }

    public List<ExternalSearchResultDTO> searchMusic(String query) {
        return search(query, false);
    }

    public List<ExternalSearchResultDTO> searchAlbums(String query) {
        return search(query, true);
    }

    public ExternalWorkDetailsDTO getMusicDetails(String externalId) {
        return details(externalId, false);
    }

    public ExternalWorkDetailsDTO getAlbumDetails(String externalId) {
        return details(externalId, true);
    }

    private List<ExternalSearchResultDTO> search(String query, boolean album) {
        if (query == null || query.isBlank()) return List.of();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/search/" + type(album))
                .queryParam("q", "{query}")
                .queryParam("limit", 20)
                .encode().buildAndExpand(query.strip()).toUri();
        try {
            JsonNode response = get(restTemplate, cache, uri, body -> body.path("data").isArray());
            List<ExternalSearchResultDTO> results = new ArrayList<>();
            for (JsonNode item : response.path("data")) {
                if (item.path("id").asLong(0) <= 0 || text(item, "title") == null) continue;
                String externalId = item.path("id").asText();
                cache.remember(identityKey(externalId, album), item);
                results.add(new ExternalSearchResultDTO(externalId, text(item, "title"),
                        artwork(item, album), releaseDate(item), text(item.path("artist"), "name")));
            }
            return results;
        } catch (RestClientException deezerFailure) {
            try {
                return album ? itunesService.searchAlbums(query) : itunesService.searchMusic(query);
            } catch (RestClientException itunesFailure) {
                // Como nas outras buscas, a indisponibilidade das duas bases vira lista vazia.
                return List.of();
            }
        }
    }

    private ExternalWorkDetailsDTO details(String externalId, boolean album) {
        if (externalId != null && externalId.startsWith(ItunesService.ID_PREFIX)) {
            String itunesId = id(externalId.substring(ItunesService.ID_PREFIX.length()));
            return album ? itunesService.getAlbumDetails(itunesId) : itunesService.getMusicDetails(itunesId);
        }
        String deezerId = id(externalId);
        try {
            JsonNode item = getDetails(deezerId, album);
            cache.remember(identityKey(deezerId, album), item);
            String releaseDate = releaseDate(item);
            if (!album && releaseDate == null && item.path("album").path("id").asLong(0) > 0) {
                // A data da faixa pode aparecer apenas nos detalhes do álbum.
                releaseDate = releaseDate(getDetails(item.path("album").path("id").asText(), true));
            }
            long duration = item.path("duration").asLong(0);
            if (album && duration <= 0) {
                duration = 0;
                for (JsonNode track : item.path("tracks").path("data")) {
                    duration += Math.max(0, track.path("duration").asLong(0));
                }
            }
            ExternalWorkDetailsDTO dto = new ExternalWorkDetailsDTO(text(item, "title"), artwork(item, album),
                    text(item.path("artist"), "name"), releaseDate, minutes(duration, 60));
            dto.setDetails(cardDetails(item, album));
            return dto;
        } catch (RestClientException deezerFailure) {
            JsonNode knownItem = cache.find(identityKey(deezerId, album));
            if (knownItem != null && text(knownItem, "title") != null
                    && text(knownItem.path("artist"), "name") != null) {
                try {
                    return itunesService.findDetails(text(knownItem, "title"),
                            text(knownItem.path("artist"), "name"), album);
                } catch (RestClientException | ExternalServiceUnavailableException itunesFailure) {
                    deezerFailure.addSuppressed(itunesFailure);
                }
            }
            // Sem título e artista conhecidos, um ID da Deezer não identifica a mesma obra no iTunes.
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes da música ou álbum. Tente novamente em instantes.",
                    deezerFailure);
        }
    }

    /** Card: a faixa mostra de qual álbum é; o álbum, quantas faixas tem. */
    private Map<String, Object> cardDetails(JsonNode item, boolean album) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (album) {
            int tracks = item.path("nb_tracks").asInt(0);
            if (tracks > 0) details.put("trackCount", tracks);
        } else {
            String albumTitle = text(item.path("album"), "title");
            if (albumTitle != null) details.put("album", albumTitle);
        }
        return details;
    }

    private JsonNode getDetails(String externalId, boolean album) {
        URI uri = URI.create(BASE_URL + "/" + type(album) + "/" + externalId);
        return get(restTemplate, cache, uri, item -> externalId.equals(item.path("id").asText())
                && text(item, "title") != null);
    }

    private String artwork(JsonNode item, boolean album) {
        JsonNode cover = album ? item : item.path("album");
        return first(text(cover, "cover_big"), text(cover, "cover_medium"), text(cover, "cover"));
    }

    private String releaseDate(JsonNode item) {
        return first(date(text(item, "release_date")), date(text(item.path("album"), "release_date")));
    }

    private String identityKey(String externalId, boolean album) {
        return "deezer:identity:" + type(album) + ":" + externalId;
    }

    private String type(boolean album) {
        return album ? "album" : "track";
    }
}
