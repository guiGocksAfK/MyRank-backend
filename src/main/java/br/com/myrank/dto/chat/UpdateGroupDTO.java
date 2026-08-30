package br.com.myrank.dto.chat;

/** Patch do grupo: campo null = não mexe. `imageUrl` vazio = remove a foto. */
public record UpdateGroupDTO(
        String name,
        String imageUrl,
        String access         // OPEN | REQUEST | CLOSED
) {}
