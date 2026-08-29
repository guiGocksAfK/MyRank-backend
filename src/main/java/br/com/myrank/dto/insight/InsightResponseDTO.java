package br.com.myrank.dto.insight;

import br.com.myrank.domain.entity.AiInsight;

import java.time.LocalDateTime;

/**
 * Resposta dos endpoints de insights: a análise em si ({@code analysis}) mais
 * metadados que o front usa pra mostrar "gerado há X" / "reaproveitado".
 */
public record InsightResponseDTO(
        InsightPayloadDTO analysis,
        String model,
        int workCount,
        boolean cached,
        LocalDateTime generatedAt
) {
    public static InsightResponseDTO of(AiInsight entity, InsightPayloadDTO analysis, boolean cached) {
        return new InsightResponseDTO(
                analysis,
                entity.getModel(),
                entity.getWorkCount(),
                cached,
                entity.getCreatedAt()
        );
    }
}
