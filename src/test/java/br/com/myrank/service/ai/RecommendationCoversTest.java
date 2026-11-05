package br.com.myrank.service.ai;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.insight.InsightPayloadDTO;
import br.com.myrank.dto.insight.InsightPayloadDTO.Recommendation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationCoversTest {

    private static InsightPayloadDTO payload(Recommendation... recs) {
        return new InsightPayloadDTO("t", "s", List.of(), List.of(), List.of(), null, List.of(recs));
    }

    private static Recommendation rec(String title, Integer year, String category, String poster) {
        return new Recommendation(title, year, category, null, "motivo", poster);
    }

    private static ExternalSearchResultDTO hit(String title, String date, String poster) {
        return new ExternalSearchResultDTO("1", title, poster, date);
    }

    @Test
    void preferTituloEAnoIguais_eUsaABuscaDoTipo() {
        List<String> buscas = new ArrayList<>();
        RecommendationCovers covers = new RecommendationCovers(Map.of(
                "serie", q -> { buscas.add("serie:" + q); return List.of(
                        hit("Arcane: League of Legends", "2021-11-06", "https://img/a1.jpg"),
                        hit("Arcane", "2021-11-06", "https://img/arcane.jpg")); }));

        InsightPayloadDTO out = covers.fill(payload(rec("Arcane", 2021, "Série", null)));

        assertThat(out.recommendations().get(0).posterUrl()).isEqualTo("https://img/arcane.jpg");
        assertThat(buscas).containsExactly("serie:Arcane");
    }

    @Test
    void nadaBate_ouTipoSemBusca_ficaVazio() {
        RecommendationCovers covers = new RecommendationCovers(Map.of(
                "filme", q -> List.of(hit("Outra coisa", "1990-01-01", "https://img/x.jpg"))));

        InsightPayloadDTO out = covers.fill(payload(
                rec("O Poderoso Chefão", 1972, "Filme", null),
                rec("Algo", 2000, "Podcast", null)));

        assertThat(out.recommendations()).extracting(Recommendation::posterUrl).containsExactly("", "");
    }

    @Test
    void jaProcurada_naoBuscaDeNovo_eErroNaoQuebra() {
        RecommendationCovers covers = new RecommendationCovers(Map.of(
                "anime", q -> { throw new IllegalStateException("fora do ar"); }));

        InsightPayloadDTO out = covers.fill(payload(
                rec("Cowboy Bebop", 1998, "Anime", "https://img/cb.jpg"),
                rec("Frieren", 2023, "Anime", null)));

        assertThat(out.recommendations()).extracting(Recommendation::posterUrl).containsExactly("https://img/cb.jpg", "");
        assertThat(covers.needsFill(out)).isFalse();
    }
}
