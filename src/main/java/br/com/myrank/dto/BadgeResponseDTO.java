package br.com.myrank.dto;

import java.time.LocalDateTime;

public record BadgeResponseDTO(
        String code,
        String bucket,
        String name,
        String description,
        String icon,
        int target,
        boolean hasProgress,
        int progress,
        boolean unlocked,
        LocalDateTime unlockedAt
) {}
