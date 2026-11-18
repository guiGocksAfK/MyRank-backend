package br.com.myrank.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Código de autorização devolvido pela janela do Google aberta pelo botão do site. */
public record GoogleCodeRequestDTO(
        @NotBlank(message = "A autorização não chegou. Tente de novo.")
        String code
) {}
