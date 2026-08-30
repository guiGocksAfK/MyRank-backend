package br.com.myrank.dto;

import java.time.LocalDateTime;

/** Uma mensagem, do ponto de vista de quem pediu (`mine`). */
public record ChatMessageDTO(
        Long id,
        Long senderId,
        boolean mine,
        String body,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {}
