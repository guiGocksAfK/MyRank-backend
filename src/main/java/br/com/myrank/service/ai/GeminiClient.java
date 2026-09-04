package br.com.myrank.service.ai;

import br.com.myrank.dto.insight.InsightChatMessageDTO;
import br.com.myrank.dto.insight.InsightPayloadDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente do Gemini via camada compatível com a API da OpenAI
 * ({@code POST {base-url}/chat/completions}). Trocar de provedor = trocar
 * {@code gemini.api.*} nas properties, sem mexer no código.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiClient(ObjectMapper objectMapper,
                        @Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.api.base-url}") String baseUrl,
                        @Value("${gemini.api.model}") String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(45).toMillis()); // geração leva 10-30s

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /**
     * Manda o prompt e devolve a análise já parseada. Qualquer falha
     * (sem chave, provedor fora do ar, JSON inválido) vira IllegalStateException
     * → 503 com mensagem amigável (ver GlobalExceptionHandler).
     */
    public InsightPayloadDTO analyze(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "A análise por IA não está configurada no servidor. Defina GEMINI_API_KEY e tente de novo.");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.7,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String raw;
        try {
            raw = http.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Chamada ao Gemini falhou: {}", e.getMessage());
            throw new IllegalStateException(
                    "O serviço de IA está instável agora. Tente gerar a análise de novo em instantes.", e);
        }

        String content = extractContent(raw);
        InsightPayloadDTO payload = parsePayload(content);

        if (payload == null || !payload.isUsable()) {
            log.warn("Gemini devolveu payload inutilizável: {}", content);
            throw new IllegalStateException(
                    "A IA respondeu num formato inesperado. Tente gerar a análise de novo.");
        }
        return payload;
    }

    /**
     * Chat de follow-up sobre uma análise. {@code context} entra como priming,
     * {@code history} são os turnos anteriores (USER/AI) e {@code question} é a
     * nova pergunta. Devolve texto puro. Falhas viram 503 amigável.
     */
    public String chat(String systemPrompt, String context,
                       List<InsightChatMessageDTO> history, String question) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "O chat da IA não está configurado no servidor. Defina GEMINI_API_KEY e tente de novo.");
        }

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", context));
        messages.add(Map.of("role", "assistant", "content",
                "Entendi. Pode perguntar o que quiser sobre essa análise."));
        if (history != null) {
            for (InsightChatMessageDTO m : history) {
                messages.add(Map.of(
                        "role", m.isUser() ? "user" : "assistant",
                        "content", m.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", question));

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.6,
                "messages", messages
        );

        String raw;
        try {
            raw = http.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Chat com o Gemini falhou: {}", e.getMessage());
            throw new IllegalStateException(
                    "O serviço de IA está instável agora. Tente perguntar de novo em instantes.", e);
        }

        String answer = extractContent(raw).trim();
        if (answer.startsWith("```")) {
            answer = answer.replaceAll("^```(?:\\w+)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        return answer;
    }

    /** choices[0].message.content da resposta estilo OpenAI. */
    private String extractContent(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual() || content.asText().isBlank()) {
                throw new IllegalStateException("resposta sem conteúdo");
            }
            return content.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "A IA respondeu num formato inesperado. Tente gerar a análise de novo.", e);
        }
    }

    /** O content é uma string JSON; alguns modelos ainda embrulham em ```json … ```. */
    private InsightPayloadDTO parsePayload(String content) {
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        try {
            return objectMapper.readValue(json, InsightPayloadDTO.class);
        } catch (Exception e) {
            return null;
        }
    }
}
