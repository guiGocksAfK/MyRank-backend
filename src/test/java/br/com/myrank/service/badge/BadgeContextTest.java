package br.com.myrank.service.badge;

import br.com.myrank.service.CreatorNames;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeContextTest {

    private static BadgeContext.WorkView serie(String creator) {
        return new BadgeContext.WorkView("serie", 9, 600, CreatorNames.keys(creator), 2010, null, false, 1L);
    }

    private static BadgeContext.WorkView obra(String type, double score, int minutes, String creator,
                                              Integer year, LocalDate added, Long tableId) {
        return new BadgeContext.WorkView(type, score, minutes, CreatorNames.keys(creator), year, added, false, tableId);
    }

    private static BadgeContext contexto(BadgeContext.WorkView... works) {
        return new BadgeContext(List.of(works), false, false, false, 0);
    }

    @Test
    void criadoresDistintos_contamCadaPessoaDoCampo_semRepetir() {
        BadgeContext context = contexto(
                serie("Vince Gilligan"),
                serie("Vince Gilligan, Peter Gould"),
                serie("vince gilligan"),
                serie(null));

        assertThat(context.distinctCreators("serie")).isEqualTo(2);
    }

    @Test
    void mesmoCriador_contaQuantasObrasTemQuemMaisAparece() {
        BadgeContext context = contexto(
                obra("album", 8, 0, "Radiohead", 1997, null, 1L),
                obra("album", 9, 0, "radiohead", 2000, null, 1L),
                obra("album", 7, 0, "Radiohead, Thom Yorke", 2007, null, 1L),
                obra("album", 8, 0, "Daft Punk", 2001, null, 1L),
                obra("musica", 8, 0, "Radiohead", 1997, null, 2L));

        assertThat(context.maxWorksBySameCreator("album")).isEqualTo(3);
        assertThat(context.maxWorksBySameCreator("livro")).isZero();
    }

    @Test
    void decadasDoTipo_ignoramOutrosTiposEAnoVazio() {
        BadgeContext context = contexto(
                obra("musica", 8, 0, "A", 1975, null, 1L),
                obra("musica", 8, 0, "B", 1979, null, 1L),
                obra("musica", 8, 0, "C", 1994, null, 1L),
                obra("musica", 8, 0, "D", null, null, 1L),
                obra("filme", 8, 0, "E", 2015, null, 2L));

        assertThat(context.distinctDecadesOfType("musica")).isEqualTo(2);
    }

    @Test
    void anoDoLancamento_valeSoQuandoAdicionouNoMesmoAno() {
        BadgeContext noAno = contexto(obra("jogo", 8, 600, "X", 2024, LocalDate.of(2024, 11, 2), 1L));
        BadgeContext depois = contexto(obra("jogo", 8, 600, "X", 2023, LocalDate.of(2024, 1, 5), 1L));
        BadgeContext semData = contexto(obra("jogo", 8, 600, "X", 2024, null, 1L));

        assertThat(noAno.hasWorkInReleaseYear("jogo")).isTrue();
        assertThat(depois.hasWorkInReleaseYear("jogo")).isFalse();
        assertThat(semData.hasWorkInReleaseYear("jogo")).isFalse();
    }

    @Test
    void obraCurta_precisaDeTempoRegistrado() {
        BadgeContext semTempo = contexto(obra("serie", 8, 0, "X", 2020, null, 1L));
        BadgeContext curta = contexto(obra("serie", 8, 540, "X", 2020, null, 1L));
        BadgeContext longa = contexto(obra("serie", 8, 1200, "X", 2020, null, 1L));

        assertThat(semTempo.hasShortWorkOfType("serie", 10)).isFalse();
        assertThat(curta.hasShortWorkOfType("serie", 10)).isTrue();
        assertThat(longa.hasShortWorkOfType("serie", 10)).isFalse();
    }

    @Test
    void tabelasDoTipo_contamTabelasDiferentes() {
        BadgeContext context = contexto(
                obra("outro", 8, 60, null, null, null, 1L),
                obra("outro", 8, 60, null, null, null, 1L),
                obra("outro", 8, 60, null, null, null, 5L),
                obra("jogo", 8, 60, null, null, null, 9L));

        assertThat(context.distinctTablesOfType("outro")).isEqualTo(2);
    }

    @Test
    void notasPorFaixa_contamDentroDoLimite() {
        BadgeContext context = contexto(
                obra("outro", 9, 0, null, null, null, 1L),
                obra("outro", 8.9, 0, null, null, null, 1L),
                obra("filme", 5, 0, null, null, null, 2L),
                obra("filme", 3, 0, null, null, null, 2L),
                obra("filme", 5.1, 0, null, null, null, 2L));

        assertThat(context.countScoreGteOfType("outro", 9.0)).isEqualTo(1);
        assertThat(context.countScoreLte(5.0)).isEqualTo(2);
    }

    @Test
    void catalogo_tem72ConquistasComCodigosUnicos() {
        assertThat(BadgeDefinition.values()).hasSize(72);
        assertThat(java.util.Arrays.stream(BadgeDefinition.values()).map(BadgeDefinition::bucket).distinct())
                .containsExactly("jogo", "filme", "serie", "livro", "anime", "manga", "musica", "album", "outro", "geral", "site");
    }
}
