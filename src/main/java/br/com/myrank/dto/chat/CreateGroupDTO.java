package br.com.myrank.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupDTO(
        @NotBlank(message = "Informe o nome do grupo.")
        @Size(max = 80, message = "O nome do grupo é longo demais.")
        String name,

        List<Long> memberIds,
        String access,        // OPEN | REQUEST | CLOSED (default CLOSED)

        @Size(max = 1000) String imageUrl,
        @Size(max = 300, message = "A descrição deve ter no máximo 300 caracteres.")
        String description
) {}
