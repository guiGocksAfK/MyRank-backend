package br.com.myrank.dto.insight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Uma mensagem do chat de follow-up de uma análise de IA.
 * {@code role} = "USER" (pergunta do usuário) ou "AI" (resposta do modelo).
 * Serializada dentro de {@code ai_insights.chat_log} (jsonb).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightChatMessageDTO(
        String role,
        String content,
        LocalDateTime at
) {
    public static final String USER = "USER";
    public static final String AI = "AI";

    public boolean isUser() {
        return USER.equals(role);
    }
}
