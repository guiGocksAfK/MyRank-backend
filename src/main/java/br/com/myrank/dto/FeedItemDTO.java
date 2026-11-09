package br.com.myrank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Um item do feed. Só os campos relevantes pro `type` vêm preenchidos:
 * - TAKE       → work, takeText, score, reações e comentários
 * - RATED_DAY  → works (as mais recentes, até 8) e workCount: as obras que a
 *                pessoa avaliou naquele dia, num card só. O id é negativo
 *                (não é um evento de verdade, então não recebe reação).
 */
public record FeedItemDTO(
        Long id,
        String type,
        ActorDTO actor,
        LocalDateTime createdAt,
        WorkMiniDTO work,
        BadgeMiniDTO badge,
        String takeText,
        Long takeId,
        int commentCount,
        boolean takeEdited,
        boolean canManage,
        BigDecimal score,
        ReactionSummaryDTO reactions,
        List<WorkMiniDTO> works,
        int workCount
) {}
