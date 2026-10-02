package br.com.myrank.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Predicate;

final class MusicCatalogSupport {

    private MusicCatalogSupport() {}

    static JsonNode get(RestTemplate restTemplate, MusicSearchCache cache, URI uri,
                        Predicate<JsonNode> valid) {
        return cache.get(uri.toString(), () -> {
            JsonNode response = restTemplate.getForObject(uri, JsonNode.class);
            // A Deezer também comunica erros no corpo de respostas HTTP 200.
            if (response == null || !response.isObject() || response.hasNonNull("error")
                    || !valid.test(response)) {
                throw new RestClientException("Resposta inválida da API de música.");
            }
            return response;
        });
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    static String date(String value) {
        if (value == null || value.length() < 10) return null;
        try {
            return LocalDate.parse(value.substring(0, 10)).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static int minutes(long duration, long unitsPerMinute) {
        // O DTO usa minutos inteiros; arredondamos só depois de somar a duração.
        return (int) Math.min(Integer.MAX_VALUE, Math.round(Math.max(0, duration) / (double) unitsPerMinute));
    }

    static String id(String value) {
        try {
            if (value != null && value.matches("[1-9][0-9]{0,18}") && Long.parseLong(value) > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // Valores fora do intervalo de long também são IDs inválidos.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID de música ou álbum inválido.");
    }
}
