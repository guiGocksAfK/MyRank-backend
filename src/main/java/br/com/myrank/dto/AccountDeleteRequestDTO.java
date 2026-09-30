package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeleteRequestDTO(
        @NotBlank(message = "Digite seu nome de usuário para confirmar.")
        @Size(max = 50)
        String confirmUsername,

        /** Código enviado ao email da conta (vale pra todas as contas, com ou sem senha). */
        @NotBlank(message = "Digite o código enviado ao seu email.")
        @Size(max = 20)
        String deletionCode
) {}
