package br.com.myrank.service.external;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Monta o `details` que vai pro card (e pra análise da IA): só entra o que veio
 * preenchido — null, texto vazio, zero e lista vazia ficam de fora.
 */
final class CardDetails {

    private final Map<String, Object> values = new LinkedHashMap<>();

    static CardDetails create() {
        return new CardDetails();
    }

    CardDetails put(String key, Object value) {
        if (value == null) return this;
        if (value instanceof String text && text.isBlank()) return this;
        if (value instanceof Number number && number.longValue() <= 0) return this;
        if (value instanceof Collection<?> list && list.isEmpty()) return this;
        values.put(key, value);
        return this;
    }

    /** Gêneros e afins: os nomes como vêm da API (sem tradução), sem repetidos. */
    <T> CardDetails names(String key, List<T> items, Function<T, String> name) {
        if (items == null) return this;
        return put(key, items.stream().filter(Objects::nonNull).map(name)
                .filter(n -> n != null && !n.isBlank()).distinct().toList());
    }

    Map<String, Object> build() {
        return values;
    }
}
