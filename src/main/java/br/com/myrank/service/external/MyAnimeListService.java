package br.com.myrank.service.external;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.dto.external.MalAnimeNodeDTO;
import br.com.myrank.dto.external.MalListResponseDTO;
import br.com.myrank.dto.external.MalMangaNodeDTO;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Integração com a API OFICIAL do MyAnimeList (api.myanimelist.net/v2).
 * Usa apenas o modo "Client ID" (header X-MAL-CLIENT-ID) — suficiente para os
 * endpoints públicos de leitura (busca, detalhes, ranking); não precisa de
 * token de usuário.
 *
 * Registrar o app em https://myanimelist.net/apiconfig e exportar MAL_CLIENT_ID.
 * Sem o client id configurado, a busca/showcase degradam para vazio e os
 * detalhes retornam erro claro (503) em vez de um 403 confuso do MAL.
 */
@Service
public class MyAnimeListService {

    private static final String BASE_URL = "https://api.myanimelist.net/v2";
    private static final String CLIENT_ID_HEADER = "X-MAL-CLIENT-ID";

    /** MAL rejeita buscas com menos de 3 caracteres (HTTP 400). */
    private static final int MIN_QUERY_LENGTH = 3;

    private static final String SEARCH_FIELDS = "id,title,main_picture,start_date";
    private static final String DETAILS_FIELDS =
            "id,title,main_picture,start_date,num_episodes,average_episode_duration,studios";
    private static final String MANGA_DETAILS_FIELDS = "id,title,main_picture,start_date,authors,num_volumes,status";

    private final RestTemplate restTemplate;
    private final String clientId;

    public MyAnimeListService(RestTemplate restTemplate,
                              @Value("${mal.client-id:}") String clientId) {
        this.restTemplate = restTemplate;
        this.clientId = clientId;
    }

    private boolean configured() {
        return clientId != null && !clientId.isBlank();
    }

    /**
     * Autocomplete de anime: GET /anime?q=...&limit=20. O MAL ordena por
     * relevância (não há parâmetro de popularidade), o que já serve bem ao
     * autocomplete. Instabilidade do MAL (5xx/timeout) → lista vazia.
     */
    public List<ExternalSearchResultDTO> searchAnime(String query) {
        return search(query, "anime", "animes");
    }

    /** Autocomplete de mangá com o mesmo client id e tratamento de falhas da busca de anime. */
    public List<ExternalSearchResultDTO> searchManga(String query) {
        return search(query, "manga", "mangás");
    }

    private List<ExternalSearchResultDTO> search(String query, String resource, String worksName) {
        if (!configured() || query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return Collections.emptyList();
        }

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/" + resource)
                .queryParam("q", query.trim())
                .queryParam("limit", 20)
                .queryParam("fields", SEARCH_FIELDS)
                .toUriString();

        try {
            MalListResponseDTO response = executeGet(url, MalListResponseDTO.class);
            return mapSearchResults(response);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 5xx do MAL ou timeout: instabilidade transitória, não-acionável no
            // autocomplete — degrada para "nenhum resultado".
            return Collections.emptyList();
        } catch (RestClientException e) {
            // 4xx (ex.: 400 query curta, 403 client id inválido) e demais falhas.
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar " + worksName + " agora. O MyAnimeList pode estar instável ou limitando requisições. Tente novamente em instantes.", e);
        }
    }

    /**
     * Pôsteres dos animes mais populares (GET /anime/ranking?ranking_type=bypopularity),
     * para o grid decorativo da home pública. Best-effort: qualquer falha → lista vazia.
     */
    public List<String> getShowcasePosters() {
        if (!configured()) {
            return Collections.emptyList();
        }

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/anime/ranking")
                .queryParam("ranking_type", "bypopularity")
                .queryParam("limit", 20)
                .queryParam("fields", "main_picture")
                .toUriString();

        MalListResponseDTO response;
        try {
            response = executeGet(url, MalListResponseDTO.class);
        } catch (RestClientException e) {
            return Collections.emptyList();
        }
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData().stream()
                .map(MalListResponseDTO.Entry::getNode)
                .filter(node -> node != null)
                .map(MalAnimeNodeDTO::resolveImageUrl)
                .filter(u -> u != null && !u.isBlank())
                .collect(Collectors.toList());
    }

    /** Detalhes completos de um anime: GET /anime/{id}?fields=... (objeto na raiz). */
    public ExternalWorkDetailsDTO getAnimeDetails(Long malId) {
        MalAnimeNodeDTO details = getDetails(malId, "anime", "anime", DETAILS_FIELDS, MalAnimeNodeDTO.class);
        return new ExternalWorkDetailsDTO(
                details.getTitle(),
                details.resolveImageUrl(),
                details.resolveStudioNames(),
                details.resolveReleaseDate(),
                details.resolveTotalMinutes()
        );
    }

    /** Detalhes de mangá: autores como criador e duração zero, sem ponderação por tempo. */
    public ExternalWorkDetailsDTO getMangaDetails(Long malId) {
        if (malId == null || malId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID de mangá inválido.");
        }
        MalMangaNodeDTO details = getDetails(malId, "manga", "mangá", MANGA_DETAILS_FIELDS, MalMangaNodeDTO.class);
        ExternalWorkDetailsDTO dto = new ExternalWorkDetailsDTO(
                details.getTitle(), details.resolveImageUrl(), details.resolveAuthorNames(),
                details.resolveReleaseDate(), 0);
        // Card: quantos volumes (0 = ainda em aberto, o MAL não informa) e a situação da publicação.
        java.util.Map<String, Object> card = new java.util.LinkedHashMap<>();
        if (details.getNumVolumes() != null && details.getNumVolumes() > 0) card.put("volumes", details.getNumVolumes());
        if (details.getStatus() != null && !details.getStatus().isBlank()) card.put("status", details.getStatus());
        dto.setDetails(card);
        return dto;
    }

    private <T extends MalAnimeNodeDTO> T getDetails(Long malId, String resource, String workName,
                                                    String fields, Class<T> responseType) {
        if (!configured()) {
            throw new ExternalServiceUnavailableException(
                    "A integração com o MyAnimeList não está configurada.", null);
        }

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/" + resource + "/" + malId)
                .queryParam("fields", fields)
                .toUriString();

        T details;
        try {
            details = executeGet(url, responseType);
        } catch (RestClientException e) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes do " + workName + " agora. O MyAnimeList pode estar instável — tente novamente em instantes.", e);
        }

        if (details == null || details.getTitle() == null) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes do " + workName + " agora. Tente novamente em instantes.", null);
        }

        return details;
    }

    private List<ExternalSearchResultDTO> mapSearchResults(MalListResponseDTO response) {
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData().stream()
                .map(MalListResponseDTO.Entry::getNode)
                .filter(node -> node != null && node.getId() != null)
                .map(node -> new ExternalSearchResultDTO(
                        String.valueOf(node.getId()),
                        node.getTitle(),
                        node.resolveImageUrl(),
                        node.resolveReleaseDate()
                ))
                .collect(Collectors.toList());
    }

    private <T> T executeGet(String url, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CLIENT_ID_HEADER, clientId);
        headers.set("accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
        return response.getBody();
    }
}
