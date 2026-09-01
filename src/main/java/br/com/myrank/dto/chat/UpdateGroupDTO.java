package br.com.myrank.dto.chat;

import jakarta.validation.constraints.Size;

/** Patch do grupo: campo null = não mexe. `imageUrl`/`description` vazio = limpa. */
public record UpdateGroupDTO(
        @Size(max = 80, message = "O nome do grupo é longo demais.")
        String name,

        @Size(max = 1000) String imageUrl,
        String access,        // OPEN | REQUEST | CLOSED

        @Size(max = 300, message = "A descrição deve ter no máximo 300 caracteres.")
        String description
) {}
