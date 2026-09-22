package br.com.myrank.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequestDTO(
        @NotBlank(message = "Informe seu email.")
        @Email(message = "Email inválido.")
        @Size(max = 254)
        String email
) {}
