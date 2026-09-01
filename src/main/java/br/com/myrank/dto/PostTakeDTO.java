package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostTakeDTO(
        @NotNull Long workId,

        @NotBlank(message = "Escreva algo no take.")
        @Size(min = 3, max = 280, message = "O take deve ter de 3 a 280 caracteres.")
        String text
) {}
