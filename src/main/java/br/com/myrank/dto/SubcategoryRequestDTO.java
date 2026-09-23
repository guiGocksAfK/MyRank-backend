package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubcategoryRequestDTO(
        @NotBlank(message = "Informe o nome da subcategoria.")
        @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
        String name
) {}
