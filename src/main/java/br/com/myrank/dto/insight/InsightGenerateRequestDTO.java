package br.com.myrank.dto.insight;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo do POST /api/insights/generate.
 * {@code workIds} = obras selecionadas no painel; {@code refresh} força uma
 * nova geração mesmo que já exista análise cacheada pra essa mesma seleção.
 */
public record InsightGenerateRequestDTO(
        @Size(max = 500, message = "Obras demais na seleção.")
        List<Long> workIds,
        boolean refresh
) {}
