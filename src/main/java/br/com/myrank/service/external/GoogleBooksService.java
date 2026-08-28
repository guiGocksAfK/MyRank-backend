package br.com.myrank.service.external;

import br.com.myrank.dto.external.*;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoogleBooksService {

    private static final String BASE_URL = "https://www.googleapis.com/books/v1/volumes";

    private final RestTemplate restTemplate;
    // Na prática obrigatória: a quota anônima do Google Books vive esgotada e responde 429.
    // Configure GOOGLE_BOOKS_API_KEY (Books API habilitada no Google Cloud Console).
    private final String apiKey;

    public GoogleBooksService(RestTemplate restTemplate,
                              @Value("${google-books.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /** Autocomplete de livros: GET /volumes?q=... */
    public List<ExternalSearchResultDTO> searchBooks(String query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("q", query)
                .queryParam("maxResults", 20)
                .queryParam("langRestrict", "pt"); // Google Books exige ISO-639-1 de 2 letras; "pt-BR" é ignorado/inválido

        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("key", apiKey);
        }

        try {
            GoogleBooksSearchResponseDTO response = restTemplate.getForObject(builder.toUriString(), GoogleBooksSearchResponseDTO.class);
            return mapSearchResults(response);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw rateLimitException(e);
        } catch (RestClientException e) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar livros agora. O Google Books pode estar indisponível — tente novamente em instantes.", e);
        }
    }

    /**
     * Capas de livros de ficção populares, para o grid decorativo da home pública.
     * Best-effort: qualquer falha (429, indisponibilidade) → lista vazia, e quem
     * chama completa com o fallback estático.
     */
    public List<String> getShowcasePosters() {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("q", "subject:fiction")
                .queryParam("orderBy", "relevance")
                .queryParam("maxResults", 20)
                .queryParam("langRestrict", "pt");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("key", apiKey);
        }

        GoogleBooksSearchResponseDTO response;
        try {
            response = restTemplate.getForObject(builder.toUriString(), GoogleBooksSearchResponseDTO.class);
        } catch (RestClientException e) {
            return Collections.emptyList();
        }
        if (response == null || response.getItems() == null) {
            return Collections.emptyList();
        }
        return response.getItems().stream()
                .filter(item -> item.getVolumeInfo() != null)
                .map(item -> item.getVolumeInfo().resolveImageUrl())
                .filter(u -> u != null && !u.isBlank())
                .map(u -> u.startsWith("http://") ? "https://" + u.substring(7) : u)
                .collect(Collectors.toList());
    }

    /** Detalhes completos de um livro: GET /volumes/{id} */
    public ExternalWorkDetailsDTO getBookDetails(String volumeId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/" + volumeId);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("key", apiKey);
        }

        GoogleBookItemDTO item;
        try {
            item = restTemplate.getForObject(builder.toUriString(), GoogleBookItemDTO.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw rateLimitException(e);
        } catch (RestClientException e) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes do livro agora. O serviço do Google Books pode estar instável — tente novamente em instantes.", e);
        }

        if (item == null || item.getVolumeInfo() == null) {
            throw new ExternalServiceUnavailableException(
                    "Não foi possível buscar os detalhes do livro agora. Tente novamente em instantes.", null);
        }

        GoogleBookVolumeInfoDTO info = item.getVolumeInfo();

        return new ExternalWorkDetailsDTO(
                info.getTitle(),
                info.resolveImageUrl(),
                info.resolveAuthorNames(),
                info.resolveDateOnly(),
                info.resolveEstimatedMinutes()
        );
    }

    /** 429 do Google Books: quota diária estourada (típico quando não há API key configurada). */
    private ExternalServiceUnavailableException rateLimitException(HttpClientErrorException e) {
        boolean semKey = apiKey == null || apiKey.isBlank();
        String detalhe = semKey
                ? " Configure uma API key do Google Books (GOOGLE_BOOKS_API_KEY) para aumentar o limite."
                : " Tente novamente em instantes.";
        return new ExternalServiceUnavailableException(
                "O Google Books atingiu o limite de requisições." + detalhe, e);
    }

    private List<ExternalSearchResultDTO> mapSearchResults(GoogleBooksSearchResponseDTO response) {
        if (response == null || response.getItems() == null) {
            return Collections.emptyList();
        }
        return response.getItems().stream()
                .filter(item -> item.getVolumeInfo() != null)
                .map(item -> new ExternalSearchResultDTO(
                        item.getId(), // Google Books já usa String nativamente (ex.: "zyTCAlFPjgYC")
                        item.getVolumeInfo().getTitle(),
                        item.getVolumeInfo().resolveImageUrl(),
                        item.getVolumeInfo().resolveDateOnly()
                ))
                .collect(Collectors.toList());
    }
}