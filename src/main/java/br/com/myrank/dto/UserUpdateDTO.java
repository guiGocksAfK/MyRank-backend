package br.com.myrank.dto;

import jakarta.validation.constraints.Size;

/** Update parcial: campo null = não mexe. `@Size` não reclama de null. */
public record UserUpdateDTO(
        @Size(min = 3, max = 50, message = "O nome de usuário deve ter de 3 a 50 caracteres.")
        String username,

        @Size(max = 500, message = "A bio deve ter no máximo 500 caracteres.")
        String bio,

        @Size(max = 8)
        String language,

        @Size(max = 1000, message = "A URL da foto é longa demais.")
        String avatarUrl,

        Boolean isPublic
) {}
