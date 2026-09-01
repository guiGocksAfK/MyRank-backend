package br.com.myrank.dto.insight;

/** Corpo do POST /api/insights/{id}/chat — uma pergunta de follow-up sobre a análise. */
public record InsightChatRequestDTO(String question) {}
