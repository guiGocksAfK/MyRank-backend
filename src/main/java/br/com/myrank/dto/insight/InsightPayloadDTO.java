package br.com.myrank.dto.insight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Shape estruturado que o modelo devolve. É o que fica salvo em
 * {@code ai_insights.payload} e o que o front renderiza nos cards.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightPayloadDTO(
        String summaryTitle,
        String summaryText,
        List<Trait> traits,
        List<TasteSlice> tasteProfile,
        Recommendation recommendation
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trait(String icon, String label, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TasteSlice(String name, int percent) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommendation(
            String title,
            Integer year,
            String category,
            int compatPercent,
            String reason
    ) {}

    /** Sanity check do que voltou do modelo antes de persistir. */
    public boolean isUsable() {
        return summaryTitle != null && !summaryTitle.isBlank()
                && summaryText != null && !summaryText.isBlank()
                && traits != null && !traits.isEmpty()
                && tasteProfile != null && !tasteProfile.isEmpty()
                && recommendation != null
                && recommendation.title() != null && !recommendation.title().isBlank();
    }
}
