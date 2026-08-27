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
public class GoogleBooksService {

    private static final String BASE_URL = "https://www.googleapis.com/books/v1/volumes";

    private final RestTemplate restTemplate;
    private final String apiKey; // opcional — Google Books funciona sem key, mas com limite menor

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
                .queryParam("langRestrict", "pt-BR");

        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("key", apiKey);
        }

        try {
            GoogleBooksSearchResponseDTO response = restTemplate.getForObject(builder.toUriString(), GoogleBooksSearchResponseDTO.class);
            return mapSearchResults(response);
        } catch (RestClientException e) {
            // Google Books indisponível no momento — devolve lista vazia em vez de propagar o erro.
            return Collections.emptyList();
        }
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