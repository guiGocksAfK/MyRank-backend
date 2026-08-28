package br.com.myrank.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuthTokenRequestDTO(
        @NotBlank(message = "Token é obrigatório.")
        String token
) {}
