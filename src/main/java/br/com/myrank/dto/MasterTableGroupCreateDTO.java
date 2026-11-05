package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MasterTableGroupCreateDTO(
        @NotBlank(message = "Informe o nome do agrupamento.")
        @Size(max = 100, message = "O nome do agrupamento é longo demais.")
        String name,
        @Size(max = 200, message = "Tabelas demais no agrupamento.")
        List<Long> categoryIds
) {}
