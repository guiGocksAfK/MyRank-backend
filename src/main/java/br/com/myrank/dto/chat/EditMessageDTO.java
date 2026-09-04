package br.com.myrank.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageDTO(
        @NotBlank(message = "A mensagem não pode ser vazia.")
        @Size(max = 2000, message = "A mensagem passa de 2000 caracteres.")
        String body
) {}
