package br.com.myrank.service.external;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monta a lista de pôsteres do grid decorativo da home pública, agregando os
 * "populares" das quatro bases externas (TMDB, RAWG, MyAnimeList, Google Books).
 *
 * Nunca bloqueia o visitante: mantém um snapshot em memória e dispara o refresh
 * em background quando ele passa do TTL. O primeiro visitante pode receber uma
 * lista vazia (ou parcial) até o primeiro refresh terminar — o frontend completa
 * com o fallback estático dele.
 */
@Service
public class ShowcaseService {

    /** Quantos pôsteres tentar entregar (o frontend usa 24 tiles). */
    private static final int TARGET = 24;

    /** Enquanto o snapshot for mais novo que isso, não refaz as chamadas externas. */
    private static final long TTL_MILLIS = 12 * 60 * 60 * 1000L;

    private final TmdbService tmdbService;
    private final RawgService rawgService;
    private final MyAnimeListService myAnimeListService;
    private final GoogleBooksService googleBooksService;

    private volatile List<String> snapshot = List.of();
    private volatile long lastRefresh = 0L;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public ShowcaseService(TmdbService tmdbService, RawgService rawgService,
                           MyAnimeListService myAnimeListService, GoogleBooksService googleBooksService) {
        this.tmdbService = tmdbService;
        this.rawgService = rawgService;
        this.myAnimeListService = myAnimeListService;
        this.googleBooksService = googleBooksService;
    }

    @PostConstruct
    void primeCacheOnStartup() {
        triggerRefreshIfStale();
    }

    /** Snapshot atual (pode estar vazio até o primeiro refresh concluir). */
    public List<String> getShowcasePosters() {
        triggerRefreshIfStale();
        return snapshot;
    }

    private void triggerRefreshIfStale() {
        boolean stale = System.currentTimeMillis() - lastRefresh > TTL_MILLIS;
        if (stale && refreshing.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    snapshot = buildPosterList();
                    lastRefresh = System.currentTimeMillis();
                } finally {
                    refreshing.set(false);
                }
            });
        }
    }

    /** Busca cada fonte (best-effort) e intercala os resultados pra variar o grid. */
    private List<String> buildPosterList() {
        List<String> tmdb = safe(tmdbService::getShowcasePosters);
        List<String> rawg = safe(rawgService::getShowcasePosters);
        List<String> anime = safe(myAnimeListService::getShowcasePosters);
        List<String> books = safe(googleBooksService::getShowcasePosters);
        org.slf4j.LoggerFactory.getLogger(ShowcaseService.class).info(
                "showcase refresh: tmdb={} rawg={} anime={} books={}",
                tmdb.size(), rawg.size(), anime.size(), books.size());
        List<List<String>> sources = List.of(tmdb, rawg, anime, books);

        LinkedHashSet<String> interleaved = new LinkedHashSet<>();
        int maxLen = sources.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < maxLen && interleaved.size() < TARGET; i++) {
            for (List<String> source : sources) {
                if (i < source.size()) {
                    interleaved.add(source.get(i));
                    if (interleaved.size() >= TARGET) break;
                }
            }
        }
        return new ArrayList<>(interleaved);
    }

    private List<String> safe(java.util.function.Supplier<List<String>> call) {
        try {
            List<String> result = call.get();
            return result != null ? result : List.of();
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
