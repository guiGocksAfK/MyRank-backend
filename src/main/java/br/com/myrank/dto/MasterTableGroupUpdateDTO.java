package br.com.myrank.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** Campos nulos ou vazios mantêm o que já existe. */
public record MasterTableGroupUpdateDTO(
        @Size(max = 100, message = "O nome do agrupamento é longo demais.")
        String name,
        @Size(max = 200, message = "Tabelas demais no agrupamento.")
        List<Long> categoryIds
) {}
