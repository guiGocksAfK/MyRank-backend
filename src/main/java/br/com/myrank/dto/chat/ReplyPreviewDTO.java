package br.com.myrank.dto.chat;

/** Prévia da mensagem que está sendo respondida. */
public record ReplyPreviewDTO(
        Long id,
        String senderName,
        String excerpt,
        boolean deleted
) {}
