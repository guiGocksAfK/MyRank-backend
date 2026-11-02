package br.com.myrank.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** Corpo do PUT /api/master-table-groups/{id}/order — ordem manual do ranking unificado. */
public record MasterTableGroupOrderDTO(
        // chaves "tabelaId:obraId"; o teto segura uma lista gigante no JSONB
        @Size(max = 1000, message = "Ordem com obras demais.")
        List<@Size(max = 40) String> order
) {}
