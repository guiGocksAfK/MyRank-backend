package br.com.myrank.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuthTokenRequestDTO(
        @NotBlank(message = "A autorização não chegou. Tente de novo.")
        String token
) {}
