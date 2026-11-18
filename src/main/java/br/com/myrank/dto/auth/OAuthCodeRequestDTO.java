package br.com.myrank.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuthCodeRequestDTO(
        @NotBlank(message = "A autorização não chegou. Tente de novo.")
        String code,
        @NotBlank(message = "A autorização não chegou. Tente de novo.")
        String redirectUri
) {}
