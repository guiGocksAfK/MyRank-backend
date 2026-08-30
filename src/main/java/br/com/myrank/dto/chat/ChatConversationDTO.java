package br.com.myrank.dto.chat;

import java.time.LocalDateTime;

/** Linha da lista de conversas. `peer` só vem em DIRECT; `name`/`imageUrl`/`access` só em GROUP. */
public record ChatConversationDTO(
        Long id,
        String type,             // DIRECT | GROUP
        String name,
        String description,
        String imageUrl,
        String access,           // OPEN | REQUEST | CLOSED
        ChatUserDTO peer,
        int memberCount,
        String myRole,           // OWNER | ADMIN | MOD | MEMBER
        int pendingRequests,     // pedidos aguardando (0 se você não modera)
        String lastMessage,
        String lastSenderName,
        boolean lastMine,
        String lastKind,         // USER | SYSTEM
        LocalDateTime lastAt,
        long unread
) {}
