package br.com.myrank.dto;

import java.util.List;

/** Corpo do PUT /api/master-table-groups/{id}/order — ordem manual do ranking unificado. */
public record MasterTableGroupOrderDTO(
        List<String> order
) {}
