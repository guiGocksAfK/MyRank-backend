package br.com.myrank.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerifyRequestDTO(
        @NotBlank(message = "Link de confirmação inválido.")
        @Size(max = 100, message = "Link de confirmação inválido.")
        String token
) {}
