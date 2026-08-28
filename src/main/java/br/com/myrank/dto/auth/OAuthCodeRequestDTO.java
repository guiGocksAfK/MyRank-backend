package br.com.myrank.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuthCodeRequestDTO(
        @NotBlank(message = "Code é obrigatório.")
        String code,
        @NotBlank(message = "Redirect URI é obrigatório.")
        String redirectUri
) {}
