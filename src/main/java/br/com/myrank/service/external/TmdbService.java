package br.com.myrank.service.external;

import br.com.myrank.dto.external.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TmdbService {

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    private final RestTemplate restTemplate;
    private final String bearerToken;

    public TmdbService(RestTemplate restTemplate,
                       @Value("${tmdb.api-token}") String bearerToken) {
        this.restTemplate = restTemplate;
        this.bearerToken = bearerToken;
    }

    /** Autocomplete de filmes: GET /search/movie?query=... */
    public List<ExternalSearchResultDTO> searchMovies(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/search/movie")
                .queryParam("query", query)
                .queryParam("language", "pt-BR")
                .queryParam("include_adult", false)
                .toUriString();

        TmdbSearchResponseDTO response = executeGet(url, TmdbSearchResponseDTO.class);
        return mapSearchResults(response);
    }

    /** Autocomplete de séries: GET /search/tv?query=... */
    public List<ExternalSearchResultDTO> searchTvShows(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/search/tv")
                .queryParam("query", query)
                .queryParam("language", "pt-BR")
                .queryParam("include_adult", false)
                .toUriString();

        TmdbSearchResponseDTO response = executeGet(url, TmdbSearchResponseDTO.class);
        return mapSearchResults(response);
    }

    /** Detalhes completos de um filme: GET /movie/{id}?append_to_response=credits */
    public ExternalWorkDetailsDTO getMovieDetails(Long tmdbId) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/movie/" + tmdbId)
                .queryParam("language", "pt-BR")
                .queryParam("append_to_response", "credits")
                .toUriString();

        TmdbMovieDetailsDTO details = executeGet(url, TmdbMovieDetailsDTO.class);

        String director = details.getCredits() != null
                ? details.getCredits().findDirectorName()
                : null;

        return new ExternalWorkDetailsDTO(
                details.getTitle(),
                buildImageUrl(details.getPosterPath()),
                director,
                details.getReleaseDate(),
                details.getRuntime() != null ? details.getRuntime() : 0
        );
    }

    /** Detalhes completos de uma série: GET /tv/{id} (created_by já vem no payload padrão) */
    public ExternalWorkDetailsDTO getTvShowDetails(Long tmdbId) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/tv/" + tmdbId)
                .queryParam("language", "pt-BR")
                .toUriString();

        TmdbTvDetailsDTO details = executeGet(url, TmdbTvDetailsDTO.class);

        return new ExternalWorkDetailsDTO(
                details.getName(),
                buildImageUrl(details.getPosterPath()),
                details.resolveCreatorNames(),
                details.getFirstAirDate(),
                details.resolveTotalMinutes()
        );
    }

    private <T> T executeGet(String url, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        headers.set("accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
        return response.getBody();
    }

    private List<ExternalSearchResultDTO> mapSearchResults(TmdbSearchResponseDTO response) {
        if (response == null || response.getResults() == null) {
            return Collections.emptyList();
        }
        return response.getResults().stream()
                .map(item -> new ExternalSearchResultDTO(
                        item.getId(),
                        item.resolveTitle(),
                        buildImageUrl(item.getPosterPath()),
                        item.resolveDate()
                ))
                .collect(Collectors.toList());
    }

    private String buildImageUrl(String posterPath) {
        if (posterPath == null || posterPath.isBlank()) {
            return null;
        }
        return IMAGE_BASE_URL + posterPath;
    }
}