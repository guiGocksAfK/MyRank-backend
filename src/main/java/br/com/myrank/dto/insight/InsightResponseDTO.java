package br.com.myrank.dto.insight;

import br.com.myrank.domain.entity.AiInsight;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resposta dos endpoints de insights: a análise ({@code analysis}), o chat de
 * follow-up ({@code chat}) e metadados que o front usa — "gerado há X",
 * "reaproveitado" e o orçamento diário de mensagens de IA
 * ({@code dailyRemaining} de {@code dailyLimit}).
 */
public record InsightResponseDTO(
        Long id,
        InsightPayloadDTO analysis,
        String model,
        int workCount,
        boolean cached,
        LocalDateTime generatedAt,
        List<InsightChatMessageDTO> chat,
        int dailyRemaining,
        int dailyLimit
) {
    public static InsightResponseDTO of(AiInsight entity,
                                        InsightPayloadDTO analysis,
                                        boolean cached,
                                        List<InsightChatMessageDTO> chat,
                                        int dailyRemaining,
                                        int dailyLimit) {
        return new InsightResponseDTO(
                entity.getId(),
                analysis,
                entity.getModel(),
                entity.getWorkCount(),
                cached,
                entity.getCreatedAt(),
                chat == null ? List.of() : chat,
                dailyRemaining,
                dailyLimit
        );
    }
}
