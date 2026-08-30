package br.com.myrank.dto.chat;

import java.time.LocalDateTime;
import java.util.List;

/** Uma mensagem. `senderName`/`senderAvatarUrl` servem pra render em grupo. */
public record ChatMessageDTO(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String senderAvatarUrl,
        boolean mine,
        String kind,          // USER | SYSTEM
        String body,          // null quando deleted
        boolean edited,
        boolean deleted,
        ReplyPreviewDTO replyTo,
        List<ReactionCountDTO> reactions,
        LocalDateTime createdAt
) {}
