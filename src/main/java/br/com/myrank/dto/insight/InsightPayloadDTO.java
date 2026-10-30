package br.com.myrank.dto.insight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Análise salva em {@code ai_insights.payload} e renderizada pelo front. O modelo
 * escreve os textos (resumo, traços, contradições, recomendação); o perfil de gosto
 * é calculado no código e entra depois ({@link #withTasteProfile}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightPayloadDTO(
        String summaryTitle,
        String summaryText,
        List<Trait> traits,
        List<Contradiction> contradictions,
        List<TasteSlice> tasteProfile,
        Recommendation recommendation
) {

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

    /** {@code compatPercent} também é das análises antigas (número inventado pela IA). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommendation(
            String title,
            Integer year,
            String category,
            Integer compatPercent,
            String reason
    ) {}

    public InsightPayloadDTO withTasteProfile(List<TasteSlice> slices) {
        return new InsightPayloadDTO(summaryTitle, summaryText, traits, contradictions, slices, recommendation);
    }

    /** Sanity check do que voltou do modelo antes de persistir (o perfil de gosto vem do código). */
    public boolean isUsable() {
        return summaryTitle != null && !summaryTitle.isBlank()
                && summaryText != null && !summaryText.isBlank()
                && traits != null && !traits.isEmpty()
                && recommendation != null
                && recommendation.title() != null && !recommendation.title().isBlank();
    }
}
