package br.com.myrank.dto;

public record SendMessageDTO(
        Long recipientId,
        String body
) {}
