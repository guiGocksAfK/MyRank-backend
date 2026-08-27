package br.com.myrank.service.external;

import br.com.myrank.dto.external.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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
     */
    public List<ExternalSearchResultDTO> searchAnime(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/anime")
                .queryParam("q", query)
                .queryParam("limit", 20)
                .queryParam("order_by", "popularity")
                .queryParam("sort", "asc") // popularity: menor número = mais popular na Jikan
                .toUriString();

        JikanSearchResponseDTO response = restTemplate.getForObject(url, JikanSearchResponseDTO.class);
        return mapSearchResults(response);
    }

    /** Detalhes completos de um anime: GET /anime/{id} */
    public ExternalWorkDetailsDTO getAnimeDetails(Long malId) {
        String url = BASE_URL + "/anime/" + malId;

        JikanDetailsResponseDTO response = restTemplate.getForObject(url, JikanDetailsResponseDTO.class);
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
                        item.getMalId(),
                        item.getTitle(),
                        item.getImages() != null && item.getImages().getJpg() != null
                                ? item.getImages().getJpg().getImageUrl()
                                : null,
                        item.getAired() != null ? item.getAired().resolveDateOnly() : null
                ))
                .collect(Collectors.toList());
    }
}