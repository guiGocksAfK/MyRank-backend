package br.com.myrank.dto.chat;

import java.time.LocalDateTime;

/** Linha da lista de conversas. `peer` só vem em DIRECT; `name` só em GROUP. */
public record ChatConversationDTO(
        Long id,
        String type,             // DIRECT | GROUP
        String name,
        ChatUserDTO peer,
        int memberCount,
        String myRole,           // OWNER | MEMBER
        String lastMessage,
        String lastSenderName,
        boolean lastMine,
        String lastKind,         // USER | SYSTEM
        LocalDateTime lastAt,
        long unread
) {}
