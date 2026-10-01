package br.com.myrank.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class MusicSearchCache {

    private final Cache<String, JsonNode> responses;

    public MusicSearchCache() {
        this(Ticker.systemTicker());
    }

    MusicSearchCache(Ticker ticker) {
        // Com mais de uma instância, o certo é usar um cache compartilhado, como Redis.
        responses = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(500)
                .ticker(ticker)
                .build();
    }

    JsonNode get(String key, Supplier<JsonNode> loader) {
        return responses.get(key, ignored -> loader.get());
    }

    void remember(String key, JsonNode value) {
        responses.put(key, value);
    }

    JsonNode find(String key) {
        return responses.getIfPresent(key);
    }
}
