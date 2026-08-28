package br.com.myrank.service.external;

import br.com.myrank.dto.external.*;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JikanService {

    private static final String BASE_URL = "https://api.jikan.moe/v4";

    private final RestTemplate restTemplate;

    public JikanService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Autocomplete de anime: GET /anime?q=...&order_by=popularity (prioriza animes mais conhecidos).
     * Sem API key — Jikan é pública, mas tem rate limit (3 req/s), então evitamos chamadas em excesso.
     *
     * A Jikan é um wrapper do MyAnimeList: quando o MAL está instável, a Jikan repassa
     * 504/502 pro nosso lado. Nesses casos devolvemos lista vazia em vez de propagar
     * a exceção (que o GlobalExceptionHandler estava mascarando como 403).
     */
    public List<ExternalSearchResultDTO> searchAnime(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/anime")
                .queryParam("q", query)
                .queryParam("limit", 20)
                .queryParam("order_by", "popularity")
                .queryParam("sort", "asc") // popularity: menor número = mais popular na Jikan
                .toUriString();

        try {
            JikanSearchResponseDTO response = restTemplate.getForObject(url, JikanSearchResponseDTO.class);
            return mapSearchResults(response);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 502/503/504 da Jikan ou timeout: quase sempre o MyAnimeList (fonte) está instável.
            // Instabilidade transitória e não-acionável pelo usuário — degrada para "nenhum resultado"
            // em vez de disparar toast de erro no autocomplete.
            return Collections.emptyList();
        } catch (RestClientException e) {
            // 4xx (ex.: 429 rate limit) e demais falhas: acionável / inesperado, propaga.
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar animes agora. A Jikan/MyAnimeList pode estar limitando requisições. Tente novamente em instantes.", e);
        }
    }

    /**
     * Pôsteres dos animes mais populares (Jikan /top/anime), para o grid decorativo
     * da home pública. Best-effort: Jikan/MAL instável → lista vazia, quem chama usa
     * o fallback estático.
     */
    public List<String> getShowcasePosters() {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/top/anime")
                .queryParam("limit", 20)
                .queryParam("filter", "bypopularity")
                .toUriString();

        JikanSearchResponseDTO response;
        try {
            response = restTemplate.getForObject(url, JikanSearchResponseDTO.class);
        } catch (RestClientException e) {
            return Collections.emptyList();
        }
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData().stream()
                .map(item -> {
                    if (item.getImages() == null || item.getImages().getJpg() == null) return null;
                    JikanImageVariantDTO jpg = item.getImages().getJpg();
                    return jpg.getLargeImageUrl() != null ? jpg.getLargeImageUrl() : jpg.getImageUrl();
                })
                .filter(u -> u != null && !u.isBlank())
                .collect(Collectors.toList());
    }

    /** Detalhes completos de um anime: GET /anime/{id} */
    public ExternalWorkDetailsDTO getAnimeDetails(Long malId) {
        String url = BASE_URL + "/anime/" + malId;

        JikanDetailsResponseDTO response;
        try {
            response = restTemplate.getForObject(url, JikanDetailsResponseDTO.class);
        } catch (RestClientException e) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes do anime agora. O MyAnimeList (fonte dos dados) pode estar instável — tente novamente em instantes.", e);
        }

        if (response == null || response.getData() == null) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes do anime agora. Tente novamente em instantes.", null);
        }

        JikanAnimeDetailsDTO details = response.getData();

        String imageUrl = details.getImages() != null && details.getImages().getJpg() != null
                ? details.getImages().getJpg().getImageUrl()
                : null;

        String releaseDate = details.getAired() != null
                ? details.getAired().resolveDateOnly()
                : null;

        return new ExternalWorkDetailsDTO(
                details.getTitle(),
                imageUrl,
                details.resolveStudioNames(),
                releaseDate,
                details.resolveTotalMinutes()
        );
    }

    private List<ExternalSearchResultDTO> mapSearchResults(JikanSearchResponseDTO response) {
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData().stream()
                .map(item -> new ExternalSearchResultDTO(
                        String.valueOf(item.getMalId()),
                        item.getTitle(),
                        item.getImages() != null && item.getImages().getJpg() != null
                                ? item.getImages().getJpg().getImageUrl()
                                : null,
                        item.getAired() != null ? item.getAired().resolveDateOnly() : null
                ))
                .collect(Collectors.toList());
    }
}