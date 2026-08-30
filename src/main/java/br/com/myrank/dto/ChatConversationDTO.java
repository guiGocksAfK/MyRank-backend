package br.com.myrank.dto;

import java.time.LocalDateTime;

/** Linha da lista de conversas: o outro usuário + prévia da última mensagem. */
public record ChatConversationDTO(
        Long peerId,
        String peerUsername,
        String peerAvatarUrl,
        String lastMessage,
        boolean lastMine,
        LocalDateTime lastAt,
        long unread
) {}
