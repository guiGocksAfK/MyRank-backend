package br.com.myrank.dto.chat;

public record SendMessageDTO(
        String body,
        Long replyToId        // opcional
) {}
