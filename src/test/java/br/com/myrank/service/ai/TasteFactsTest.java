package br.com.myrank.service.ai;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.insight.InsightPayloadDTO.TasteSlice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TasteFactsTest {

    private final Category movies = category(1L, "🎬 Filmes");
    private final Category series = category(2L, "📺 Séries");
    private final Category songs = category(3L, "🎵 Músicas");
    private long nextId = 1;

    private static Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private Work work(String title, Category table, TableTemplate template, String score,
                      int minutes, String creator, List<String> genres) {
        Work w = new Work();
        w.setId(nextId++);
        w.setTitle(title);
        w.setCategory(table);
        w.setTemplate(template);
        w.setScore(new BigDecimal(score));
        w.setFinalScore(new BigDecimal(score));
        w.setTimeMinutes(minutes);
        w.setCreator(creator);
        w.setReleaseDate(LocalDate.of(2010, 1, 1));
        w.setDetails(genres.isEmpty() ? Map.of() : Map.of("genres", genres));
        return w;
    }

    /** Como o InsightService entrega: da maior nota final pra menor. */
    private static List<Work> sorted(List<Work> works) {
        List<Work> copy = new ArrayList<>(works);
        copy.sort(Comparator.comparing(Work::getFinalScore).reversed());
        return copy;
    }

    @Test
    void linhaDaObra_trazTudoQueAIaPrecisa() {
        Work bb = work("Breaking Bad", series, TableTemplate.TV, "10", 3480, "Vince Gilligan",
                List.of("Drama", "Crime", "Thriller", "Suspense"));
        TasteFacts facts = new TasteFacts(List.of(bb), Map.of(bb.getId(), "o melhor final da TV"));

        assertThat(facts.ranking()).isEqualTo(
                "\"Breaking Bad\" | série | Vince Gilligan | 2010 | nota 10 | 58h | #1 em 📺 Séries"
                + " | Drama, Crime, Thriller | take: \"o melhor final da TV\"\n");
    }

    @Test
    void musica_naoMostraTempo_ePosicaoEhPorTabela() {
        List<Work> works = sorted(List.of(
                work("Thriller", songs, TableTemplate.MUSIC, "10", 6, "Michael Jackson", List.of()),
                work("Dark", series, TableTemplate.TV, "9", 1300, "Baran bo Odar", List.of()),
                work("Billie Jean", songs, TableTemplate.MUSIC, "8", 5, "Michael Jackson", List.of())));

        String ranking = new TasteFacts(works, Map.of()).ranking();

        assertThat(ranking).contains("\"Billie Jean\" | música | Michael Jackson | 2010 | nota 8 | #2 em 🎵 Músicas\n");
        assertThat(ranking).contains("\"Dark\" | série | Baran bo Odar | 2010 | nota 9 | 22h | #1 em 📺 Séries\n");
    }

    @Test
    void fatos_calculamRigorCriadoresETensoes() {
        List<Work> works = sorted(List.of(
                work("Hereditário", movies, TableTemplate.MOVIE, "4", 127, "Ari Aster", List.of("Terror")),
                work("Midsommar", movies, TableTemplate.MOVIE, "9", 148, "Ari Aster", List.of("Terror")),
                work("O Iluminado", movies, TableTemplate.MOVIE, "9.5", 146, "Stanley Kubrick", List.of("Terror")),
                work("Corra!", movies, TableTemplate.MOVIE, "9", 104, "Jordan Peele", List.of("Terror"))));

        String summary = new TasteFacts(works, Map.of()).summary();

        assertThat(summary).contains("média geral 7,9 em 4 obras, 0 com nota 10 — equilibrado");
        assertThat(summary).contains("Gêneros (2+ obras): Terror 7,9 (4)");
        assertThat(summary).contains("Criadores (2+ obras): Ari Aster 6,5 (2)");
        assertThat(summary).contains("\"Hereditário\" (4) em Terror, onde a média é 7,9");
    }

    @Test
    void perfilDeGosto_usaGeneros_quandoHaPeloMenosTres() {
        List<Work> works = sorted(List.of(
                work("A", movies, TableTemplate.MOVIE, "10", 100, "x", List.of("Drama", "Crime")),
                work("B", movies, TableTemplate.MOVIE, "8", 100, "y", List.of("Drama", "Crime", "Terror")),
                work("C", movies, TableTemplate.MOVIE, "6", 100, "z", List.of("Drama", "Terror"))));

        List<TasteSlice> profile = new TasteFacts(works, Map.of()).tasteProfile();

        assertThat(profile).extracting(TasteSlice::name).containsExactly("Drama", "Crime", "Terror");
        assertThat(profile.get(0).average()).isEqualTo(8.0);
        assertThat(profile.get(0).count()).isEqualTo(3);
        assertThat(profile.get(0).percent()).isNull();
    }

    @Test
    void perfilDeGosto_caiProTipo_semGenerosGuardados() {
        List<Work> works = sorted(List.of(
                work("A", movies, TableTemplate.MOVIE, "10", 100, "x", List.of()),
                work("B", movies, TableTemplate.MOVIE, "9", 100, "y", List.of()),
                work("C", series, TableTemplate.TV, "7", 100, "z", List.of())));

        assertThat(new TasteFacts(works, Map.of()).tasteProfile())
                .containsExactly(new TasteSlice("Filmes", null, 9.5, 2), new TasteSlice("Séries", null, 7.0, 1));
    }

    @Test
    void takeLongo_eCortado() {
        Work w = work("X", movies, TableTemplate.MOVIE, "8", 90, "y", List.of());
        String ranking = new TasteFacts(List.of(w), Map.of(w.getId(), "a".repeat(400))).ranking();

        assertThat(ranking).contains("take: \"" + "a".repeat(159) + "…\"");
    }
}
