package br.com.myrank.dto.chat;

/** Contagem de um emoji numa mensagem + se o pedinte reagiu com ele. */
public record ReactionCountDTO(
        String emoji,
        int count,
        boolean mine
) {}
