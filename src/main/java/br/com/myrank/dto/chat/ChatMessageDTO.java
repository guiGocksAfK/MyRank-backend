package br.com.myrank.dto.chat;

import java.time.LocalDateTime;

/** Uma mensagem. `senderName`/`senderAvatarUrl` servem pra render em grupo. */
public record ChatMessageDTO(
        Long id,
        Long senderId,
        String senderName,
        String senderAvatarUrl,
        boolean mine,
        String kind,          // USER | SYSTEM
        String body,
        LocalDateTime createdAt
) {}
