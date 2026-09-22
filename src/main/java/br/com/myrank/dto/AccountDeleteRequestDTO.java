package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeleteRequestDTO(
        /** Obrigatória só se a conta tem senha (conta só com Google/Discord não tem). */
        @Size(max = 100)
        String password,

        @NotBlank(message = "Digite seu nome de usuário para confirmar.")
        @Size(max = 50)
        String confirmUsername
) {}
