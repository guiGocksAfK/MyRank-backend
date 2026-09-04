package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /social/takes/{id}/comments. `parentCommentId` null = comentário raiz. */
public record PostCommentDTO(
        @NotBlank(message = "Escreva um comentário.")
        @Size(max = 500, message = "O comentário deve ter no máximo 500 caracteres.")
        String text,

        Long parentCommentId
) {}
