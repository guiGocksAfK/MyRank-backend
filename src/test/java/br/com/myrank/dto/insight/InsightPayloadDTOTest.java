package br.com.myrank.dto.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InsightPayloadDTOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static InsightPayloadDTO.Recommendation reco(String title) {
        return new InsightPayloadDTO.Recommendation(title, 2000, "Filme", null, "r", null);
    }

    private static InsightPayloadDTO withRecos(InsightPayloadDTO.Recommendation... recos) {
        return new InsightPayloadDTO("T", "S", List.of(), List.of(), List.of(), null, List.of(recos));
    }

    @Test
    void analiseAntigaComUmaRecomendacaoViraLista() throws Exception {
        InsightPayloadDTO old = mapper.readValue("""
                {"summaryTitle":"T","summaryText":"S","traits":[{"icon":"x","label":"l","description":"d"}],
                 "recommendation":{"title":"Hunter x Hunter","year":2011,"category":"Anime","reason":"r"}}""",
                InsightPayloadDTO.class);

        assertThat(old.recommendations()).extracting(InsightPayloadDTO.Recommendation::title)
                .containsExactly("Hunter x Hunter");
        assertThat(old.isUsable()).isTrue();
    }

    @Test
    void jsonSalvoSoTemALista() throws Exception {
        String json = mapper.writeValueAsString(withRecos(reco("A")));

        assertThat(json).contains("\"recommendations\"").doesNotContain("\"recommendation\"");
    }

    @Test
    void limpezaTiraObraDoRankingRepetidaESemTituloECortaEmTres() {
        InsightPayloadDTO clean = withRecos(
                reco("Breaking Bad"), reco("Dark"), reco("dark "), reco(" "),
                reco("Hades"), reco("Arcane"), reco("Fargo"))
                .withCleanRecommendations(Set.of("breaking bad", "One Piece"));

        assertThat(clean.recommendations()).extracting(InsightPayloadDTO.Recommendation::title)
                .containsExactly("Dark", "Hades", "Arcane");
    }

    @Test
    void semNenhumaRecomendacaoComTituloNaoServe() {
        assertThat(withRecos(reco(" ")).isUsable()).isFalse();
    }
}
