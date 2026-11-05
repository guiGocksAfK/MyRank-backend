package br.com.myrank.service.external;

import br.com.myrank.dto.external.*;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import br.com.myrank.service.CreatorNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
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

        TmdbSearchResponseDTO response = executeGetSilently(url, TmdbSearchResponseDTO.class);
        return mapSearchResults(response);
    }

    /** Autocomplete de séries: GET /search/tv?query=... */
    public List<ExternalSearchResultDTO> searchTvShows(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/search/tv")
                .queryParam("query", query)
                .queryParam("language", "pt-BR")
                .queryParam("include_adult", false)
                .toUriString();

        TmdbSearchResponseDTO response = executeGetSilently(url, TmdbSearchResponseDTO.class);
        return mapSearchResults(response);
    }

    /**
     * Pôsteres de filmes e séries em alta na semana (TMDB /trending), usado no grid
     * decorativo da home pública. Best-effort: se a TMDB falhar, devolve lista vazia
     * e quem chama completa com o fallback estático.
     */
    public List<String> getShowcasePosters() {
        List<String> posters = new java.util.ArrayList<>();
        posters.addAll(fetchTrendingPosters("/trending/movie/week"));
        posters.addAll(fetchTrendingPosters("/trending/tv/week"));
        return posters;
    }

    private List<String> fetchTrendingPosters(String path) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + path)
                .queryParam("language", "pt-BR")
                .toUriString();

        TmdbSearchResponseDTO response = executeGetSilently(url, TmdbSearchResponseDTO.class);
        if (response == null || response.getResults() == null) {
            return Collections.emptyList();
        }
        return response.getResults().stream()
                .map(item -> buildImageUrl(item.getPosterPath()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** Detalhes completos de um filme: GET /movie/{id}?append_to_response=credits */
    public ExternalWorkDetailsDTO getMovieDetails(Long tmdbId) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/movie/" + tmdbId)
                .queryParam("language", "pt-BR")
                .queryParam("append_to_response", "credits")
                .toUriString();

        TmdbMovieDetailsDTO details = executeGetOrThrow(url, TmdbMovieDetailsDTO.class);

        String director = details.getCredits() != null
                ? details.getCredits().findDirectorName()
                : null;

        ExternalWorkDetailsDTO dto = new ExternalWorkDetailsDTO(
                details.getTitle(),
                buildImageUrl(details.getPosterPath()),
                director,
                details.getReleaseDate(),
                details.getRuntime() != null ? details.getRuntime() : 0
        );
        // Ano e duração já vão nos campos normais; aqui só os gêneros (pra IA, não aparecem no card).
        dto.setDetails(CardDetails.create()
                .names("genres", details.getGenres(), TmdbGenreDTO::getName)
                .build());
        return dto;
    }

    /** Detalhes completos de uma série: GET /tv/{id} (created_by já vem no payload padrão) */
    public ExternalWorkDetailsDTO getTvShowDetails(Long tmdbId) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/tv/" + tmdbId)
                .queryParam("language", "pt-BR")
                .toUriString();

        TmdbTvDetailsDTO details = executeGetOrThrow(url, TmdbTvDetailsDTO.class);

        ExternalWorkDetailsDTO dto = new ExternalWorkDetailsDTO(
                details.getName(),
                buildImageUrl(details.getPosterPath()),
                details.resolveCreatorNames(),
                details.getFirstAirDate(),
                details.resolveTotalMinutes()
        );
        dto.setDetails(CardDetails.create()
                .put("seasons", details.getNumberOfSeasons())
                .put("status", details.getStatus())
                .names("genres", details.getGenres(), TmdbGenreDTO::getName)
                .build());
        return dto;
    }

    /** Usado na busca/autocomplete: se a TMDB falhar, devolve null (vira lista vazia) em vez de propagar. */
    // ── Foto dos criadores (tela de Criadores) ───────────────────────────

    private static final String PHOTO_BASE_URL = "https://image.tmdb.org/t/p/w185";

    /**
     * Foto de perfil de uma pessoa (diretor, criador de série, autor). Só aceita nome
     * igual (sem acento/maiúscula) pra não pegar outra pessoa; entre homônimos, prefere
     * quem trabalha atrás das câmeras. Null = não achou; erro da TMDB sobe (quem chama
     * não guarda o "não achou" nesse caso).
     */
    public String findPersonPhoto(String name) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/search/person")
                .queryParam("query", name)
                .queryParam("include_adult", false)
                .toUriString();
        TmdbPeopleSearchDTO response = executeGet(url, TmdbPeopleSearchDTO.class);
        List<TmdbPeopleSearchDTO.Result> same = sameName(response, name).stream()
                .filter(r -> r.getProfilePath() != null && !r.getProfilePath().isBlank())
                .toList();
        return same.stream()
                .filter(r -> !"Acting".equals(r.getKnownForDepartment()))
                .findFirst()
                .or(() -> same.stream().findFirst())
                .map(r -> PHOTO_BASE_URL + r.getProfilePath())
                .orElse(null);
    }

    /** Logo de um estúdio/empresa, só com nome igual. Null = não achou. */
    public String findCompanyLogo(String name) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/search/company")
                .queryParam("query", name)
                .toUriString();
        TmdbPeopleSearchDTO response = executeGet(url, TmdbPeopleSearchDTO.class);
        return sameName(response, name).stream()
                .filter(r -> r.getLogoPath() != null && !r.getLogoPath().isBlank())
                .findFirst()
                .map(r -> PHOTO_BASE_URL + r.getLogoPath())
                .orElse(null);
    }

    private static List<TmdbPeopleSearchDTO.Result> sameName(TmdbPeopleSearchDTO response, String name) {
        if (response == null || response.getResults() == null) return List.of();
        String key = CreatorNames.key(name);
        return response.getResults().stream()
                .filter(r -> r.getName() != null && CreatorNames.key(r.getName()).equals(key))
                .toList();
    }

    private <T> T executeGetSilently(String url, Class<T> responseType) {
        try {
            return executeGet(url, responseType);
        } catch (RestClientException e) {
            return null;
        }
    }

    /** Usado nos detalhes: se a TMDB falhar, propaga como erro claro (503), nunca um 403 confuso. */
    private <T> T executeGetOrThrow(String url, Class<T> responseType) {
        try {
            return executeGet(url, responseType);
        } catch (RestClientException e) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes agora. O serviço da TMDB pode estar instável — tente novamente em instantes.", e);
        }
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
                        String.valueOf(item.getId()),
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