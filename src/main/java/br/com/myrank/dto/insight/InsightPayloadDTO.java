package br.com.myrank.dto.insight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Análise salva em {@code ai_insights.payload} e renderizada pelo front. O modelo
 * escreve os textos (resumo, traços, contradições, recomendações); o perfil de gosto
 * é calculado no código e entra depois ({@link #withTasteProfile}).
 *
 * <p>{@code recommendation} (uma só) é das análises antigas, de antes do prompt v3.
 * Ao ler uma delas ela vira a lista {@code recommendations} com um item, então o
 * front só precisa olhar a lista.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightPayloadDTO(
        String summaryTitle,
        String summaryText,
        List<Trait> traits,
        List<Contradiction> contradictions,
        List<TasteSlice> tasteProfile,
        @JsonInclude(JsonInclude.Include.NON_NULL) Recommendation recommendation,
        List<Recommendation> recommendations
) {

    /** Quantas recomendações a análise mostra. */
    public static final int MAX_RECOMMENDATIONS = 3;

    public InsightPayloadDTO {
        if ((recommendations == null || recommendations.isEmpty()) && recommendation != null) {
            recommendations = List.of(recommendation);
        }
        recommendation = null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trait(String icon, String label, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contradiction(String icon, String text) {}

    /**
     * Uma fatia do perfil de gosto: média real e quantidade de obras. {@code percent}
     * é das análises antigas, quando a própria IA inventava a porcentagem.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TasteSlice(String name, Integer percent, Double average, Integer count) {}

    /**
     * {@code compatPercent} também é das análises antigas (número inventado pela IA).
     * {@code posterUrl} é a capa achada na API do tipo, guardada na geração: nulo =
     * ainda não procurou (análise antiga); vazio = procurou e não achou.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommendation(
            String title,
            Integer year,
            String category,
            Integer compatPercent,
            String reason,
            String posterUrl
    ) {
        boolean hasTitle() {
            return title != null && !title.isBlank();
        }

        public Recommendation withPosterUrl(String url) {
            return new Recommendation(title, year, category, compatPercent, reason, url);
        }
    }

    public InsightPayloadDTO withRecommendations(List<Recommendation> list) {
        return new InsightPayloadDTO(summaryTitle, summaryText, traits, contradictions, tasteProfile, null, list);
    }

    public InsightPayloadDTO withTasteProfile(List<TasteSlice> slices) {
        return new InsightPayloadDTO(summaryTitle, summaryText, traits, contradictions, slices, null, recommendations);
    }

    /**
     * Limpa as recomendações: tira as sem título, as repetidas e as que já estão no
     * ranking (o prompt proíbe, mas o modelo às vezes escorrega), e corta em
     * {@link #MAX_RECOMMENDATIONS}. Títulos comparados sem diferenciar maiúsculas.
     */
    public InsightPayloadDTO withCleanRecommendations(Set<String> rankedTitles) {
        Set<String> ranked = rankedTitles.stream().map(InsightPayloadDTO::norm).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        List<Recommendation> clean = recommendations == null ? List.of() : recommendations.stream()
                .filter(r -> r != null && r.hasTitle())
                .filter(r -> !ranked.contains(norm(r.title())))
                .filter(r -> seen.add(norm(r.title())))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
        return new InsightPayloadDTO(summaryTitle, summaryText, traits, contradictions, tasteProfile, null, clean);
    }

    private static String norm(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }

    /** Sanity check do que voltou do modelo antes de persistir (o perfil de gosto vem do código). */
    public boolean isUsable() {
        return summaryTitle != null && !summaryTitle.isBlank()
                && summaryText != null && !summaryText.isBlank()
                && traits != null && !traits.isEmpty()
                && recommendations != null
                && recommendations.stream().anyMatch(r -> r != null && r.hasTitle());
    }
}
