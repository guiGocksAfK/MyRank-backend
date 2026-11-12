package br.com.myrank.service.ai;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.insight.InsightPayloadDTO;
import br.com.myrank.dto.insight.InsightPayloadDTO.Recommendation;
import br.com.myrank.service.external.DeezerService;
import br.com.myrank.service.external.GoogleBooksService;
import br.com.myrank.service.external.MyAnimeListService;
import br.com.myrank.service.external.RawgService;
import br.com.myrank.service.external.TmdbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Capa das recomendações da IA. O modelo só manda nome, ano e tipo; aqui a obra é
 * procurada na API do tipo (a mesma da busca de "Adicionar obra") e a capa fica
 * guardada na análise, então a tela não busca nada a cada abertura.
 * Prefere título igual e o mesmo ano; se nada bater, fica sem capa ("").
 */
@Component
public class RecommendationCovers {

    private static final Logger log = LoggerFactory.getLogger(RecommendationCovers.class);

    /** Tipo escrito pelo modelo (ver InsightPromptBuilder) → busca da API certa. */
    private final Map<String, Function<String, List<ExternalSearchResultDTO>>> searchers;

    @Autowired
    public RecommendationCovers(TmdbService tmdb, MyAnimeListService mal, RawgService rawg,
                                GoogleBooksService books, DeezerService deezer) {
        this(Map.of(
                "filme", tmdb::searchMovies,
                "serie", tmdb::searchTvShows,
                "anime", mal::searchAnime,
                "manga", mal::searchManga,
                "jogo", rawg::searchGames,
                "livro", books::searchBooks,
                "musica", deezer::searchMusic,
                "album", deezer::searchAlbums));
    }

    RecommendationCovers(Map<String, Function<String, List<ExternalSearchResultDTO>>> searchers) {
        this.searchers = searchers;
    }

    /** Procura a capa das recomendações que ainda não procuraram (posterUrl nulo). */
    public InsightPayloadDTO fill(InsightPayloadDTO payload) {
        if (payload == null || payload.recommendations() == null) return payload;
        return payload.withRecommendations(payload.recommendations().stream()
                .map(r -> r == null || r.posterUrl() != null ? r : r.withPosterUrl(find(r)))
                .toList());
    }

    /** Se alguma recomendação ainda não teve a capa procurada (análise antiga). */
    public boolean needsFill(InsightPayloadDTO payload) {
        return payload != null && payload.recommendations() != null
                && payload.recommendations().stream().anyMatch(r -> r != null && r.posterUrl() == null);
    }

    private String find(Recommendation r) {
        Function<String, List<ExternalSearchResultDTO>> search = searchers.get(norm(r.category()));
        if (search == null || r.title() == null) return "";
        try {
            List<ExternalSearchResultDTO> results = search.apply(r.title());
            if (results == null) return "";
            String title = norm(r.title());
            ExternalSearchResultDTO best = null;
            int bestScore = 3;
            for (ExternalSearchResultDTO x : results) {
                if (x == null || x.getPosterUrl() == null || x.getPosterUrl().isBlank()) continue;
                int s = score(x, title, r.year());
                if (s < bestScore) {
                    bestScore = s;
                    best = x;
                    if (s == 0) break;
                }
            }
            return best != null ? best.getPosterUrl() : "";
        } catch (RuntimeException e) {
            log.warn("Sem capa pra recomendação '{}': {}", r.title(), e.getMessage());
            return "";
        }
    }

    /** 0 = título e ano batem; 1 = só o título; 2 = só o ano; 3 = nenhum. */
    private static int score(ExternalSearchResultDTO x, String title, Integer year) {
        boolean sameTitle = title.equals(norm(x.getTitle()));
        boolean sameYear = year != null && x.getReleaseDate() != null && x.getReleaseDate().startsWith(String.valueOf(year));
        if (sameTitle && sameYear) return 0;
        if (sameTitle) return 1;
        if (sameYear) return 2;
        return 3;
    }

    private static String norm(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
