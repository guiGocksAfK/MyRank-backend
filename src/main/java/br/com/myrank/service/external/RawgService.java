package br.com.myrank.service.external;

import br.com.myrank.dto.external.*;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RawgService {

    private static final String BASE_URL = "https://api.rawg.io/api";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public RawgService(RestTemplate restTemplate,
                       @Value("${rawg.api-key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /** Autocomplete de jogos: GET /games?search=...&ordering=-rating (prioriza jogos mais bem avaliados). */
    public List<ExternalSearchResultDTO> searchGames(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/games")
                .queryParam("key", apiKey)
                .queryParam("search", query)
                .queryParam("page_size", 20)
                .queryParam("ordering", "-rating")
                .toUriString();

        RawgSearchResponseDTO response;
        try {
            response = restTemplate.getForObject(url, RawgSearchResponseDTO.class);
        } catch (RestClientException e) {
            // RAWG indisponível no momento — devolve lista vazia em vez de propagar o erro.
            return Collections.emptyList();
        }
        return mapSearchResults(response);
    }

    /** Detalhes completos de um jogo: GET /games/{id}?key=... */
    public ExternalWorkDetailsDTO getGameDetails(Long rawgId) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/games/" + rawgId)
                .queryParam("key", apiKey)
                .toUriString();

        RawgGameDetailsDTO details;
        try {
            details = restTemplate.getForObject(url, RawgGameDetailsDTO.class);
        } catch (RestClientException e) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes do jogo agora. O serviço da RAWG pode estar instável — tente novamente em instantes.", e);
        }

        if (details == null) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes do jogo agora. Tente novamente em instantes.", null);
        }

        return new ExternalWorkDetailsDTO(
                details.getName(),
                details.getBackgroundImage(), // já vem como URL completa, sem precisar montar prefixo
                details.resolveDeveloperNames(),
                details.getReleased(),
                details.resolveTimeMinutes()
        );
    }

    private List<ExternalSearchResultDTO> mapSearchResults(RawgSearchResponseDTO response) {
        if (response == null || response.getResults() == null) {
            return Collections.emptyList();
        }
        return response.getResults().stream()
                .map(item -> new ExternalSearchResultDTO(
                        item.getId(),
                        item.getName(),
                        item.getBackgroundImage(),
                        item.getReleased()
                ))
                .collect(Collectors.toList());
    }
}