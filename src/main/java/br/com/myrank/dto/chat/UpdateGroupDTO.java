package br.com.myrank.dto.chat;

/** Patch do grupo: campo null = não mexe. `imageUrl`/`description` vazio = limpa. */
public record UpdateGroupDTO(
        String name,
        String imageUrl,
        String access,        // OPEN | REQUEST | CLOSED
        String description
) {}
