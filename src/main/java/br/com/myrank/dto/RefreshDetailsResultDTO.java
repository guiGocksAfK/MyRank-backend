package br.com.myrank.dto;

import java.util.List;

/** Resultado de preencher os detalhes de todas as obras: o que mudou e o que ficou pra fazer à mão. */
public record RefreshDetailsResultDTO(int updated, List<String> updatedTitles, List<Skipped> skipped) {

    public record Skipped(String title, String reason) {}
}
