package br.com.myrank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Um item do feed. Só os campos relevantes pro `type` vêm preenchidos:
 * - RATED / ADDED  → work, score
 * - BADGE          → badge
 * - TAKE           → work, takeText, score
 */
public record FeedItemDTO(
        Long id,
        String type,
        ActorDTO actor,
        LocalDateTime createdAt,
        WorkMiniDTO work,
        BadgeMiniDTO badge,
        String takeText,
        BigDecimal score,
        ReactionSummaryDTO reactions
) {}
