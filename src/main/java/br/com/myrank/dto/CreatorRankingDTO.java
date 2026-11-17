package br.com.myrank.dto;

import java.util.List;

/**
 * Uma linha do ranking de criadores. {@code name} junta quem tem exatamente as mesmas
 * obras ("David Benioff & D. B. Weiss"). {@code average} é a média pura das notas;
 * {@code score} é o que ordena (igual à média, ou ponderada pelo tempo com o bônus de
 * volume). {@code works} são as obras mais bem avaliadas dele, no máximo quatro.
 */
public record CreatorRankingDTO(
        String id,
        String name,
        String type,
        int count,
        double average,
        double score,
        List<WorkMiniDTO> works
) {}
