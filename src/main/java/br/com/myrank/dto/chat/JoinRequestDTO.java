package br.com.myrank.dto.chat;

import java.time.LocalDateTime;

public record JoinRequestDTO(
        Long userId,
        String username,
        String avatarUrl,
        LocalDateTime createdAt
) {}
