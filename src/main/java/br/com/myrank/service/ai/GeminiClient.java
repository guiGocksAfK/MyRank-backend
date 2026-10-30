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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente do Gemini via camada compatível com a API da OpenAI
 * ({@code POST {base-url}/chat/completions}). Trocar de provedor = trocar
 * {@code gemini.api.*} nas properties, sem mexer no código.
 *
 * Cada chamada tenta duas vezes: o plano gratuito recusa pedidos de vez em quando
 * (429/5xx) e às vezes a resposta vem com o JSON quebrado. Erro do nosso lado (4xx)
 * não é repetido.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final int ATTEMPTS = 2;

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final Duration retryPause;

    public GeminiClient(ObjectMapper objectMapper,
                        @Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.api.base-url}") String baseUrl,
                        @Value("${gemini.api.model}") String model,
                        @Value("${gemini.api.reasoning-effort:none}") String reasoningEffort,
                        @Value("${gemini.api.retry-pause-ms:1000}") long retryPauseMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
        this.retryPause = Duration.ofMillis(retryPauseMs);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

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
     * Manda o prompt e devolve a análise já parseada. Qualquer falha que sobre
     * depois das tentativas (sem chave, provedor fora do ar, JSON inválido) vira
     * IllegalStateException → 503 com mensagem amigável (ver GlobalExceptionHandler).
     */
    public InsightPayloadDTO analyze(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "A análise por IA não está configurada no servidor. Defina GEMINI_API_KEY e tente de novo.");
        }

        Map<String, Object> body = body(0.5, List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("max_tokens", 2048); // no modo JSON o modelo às vezes não para de gerar; a análise usa ~650

        for (int attempt = 1; ; attempt++) {
            boolean last = attempt == ATTEMPTS;
            try {
                String content = extractContent(post(body));
                InsightPayloadDTO payload = parsePayload(content);
                if (payload != null && payload.isUsable()) {
                    return payload;
                }
                log.warn("Gemini devolveu payload inutilizável (tentativa {}): {}", attempt, content);
            } catch (RetryableFailure e) {
                log.warn("Chamada ao Gemini falhou (tentativa {}): {}", attempt, e.getMessage());
            } catch (RestClientResponseException e) {
                log.warn("Gemini recusou o pedido: {}", e.getMessage());
                throw new IllegalStateException(
                        "O serviço de IA recusou o pedido. Tente gerar a análise de novo mais tarde.", e);
            }
            if (last) {
                throw new IllegalStateException(
                        "O serviço de IA está instável agora. Tente gerar a análise de novo em instantes.");
            }
            pause();
        }
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

        List<Map<String, Object>> messages = new ArrayList<>();
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
        Map<String, Object> body = body(0.6, messages);
        body.put("max_tokens", 1024);

        for (int attempt = 1; ; attempt++) {
            try {
                String answer = extractContent(post(body)).trim();
                if (answer.startsWith("```")) {
                    answer = answer.replaceAll("^```(?:\\w+)?\\s*", "").replaceAll("\\s*```$", "").trim();
                }
                return answer;
            } catch (RetryableFailure e) {
                log.warn("Chat com o Gemini falhou (tentativa {}): {}", attempt, e.getMessage());
                if (attempt == ATTEMPTS) {
                    throw new IllegalStateException(
                            "O serviço de IA está instável agora. Tente perguntar de novo em instantes.");
                }
            } catch (RestClientResponseException e) {
                log.warn("Gemini recusou a pergunta: {}", e.getMessage());
                throw new IllegalStateException(
                        "O serviço de IA recusou o pedido. Tente perguntar de novo mais tarde.", e);
            }
            pause();
        }
    }

    /**
     * Corpo comum. O raciocínio interno do Gemini 2.5 Flash fica desligado por padrão:
     * com ele, a análise levava ~25s; sem ele, ~3s, com o mesmo resultado em JSON.
     */
    private Map<String, Object> body(double temperature, List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        if (!reasoningEffort.isEmpty()) {
            body.put("reasoning_effort", reasoningEffort);
        }
        body.put("messages", messages);
        return body;
    }

    /** POST no provedor. Fora do ar, limite de uso (429) ou 5xx viram {@link RetryableFailure}. */
    private String post(Map<String, Object> body) {
        try {
            return http.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429 || status >= 500) {
                throw new RetryableFailure(status + " " + e.getStatusText());
            }
            throw e;
        } catch (ResourceAccessException e) {
            throw new RetryableFailure("sem resposta: " + e.getMessage());
        }
    }

    /** choices[0].message.content da resposta estilo OpenAI. */
    private String extractContent(String raw) {
        try {
            JsonNode content = objectMapper.readTree(raw)
                    .path("choices").path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText();
            }
        } catch (Exception e) {
            // cai no erro abaixo
        }
        throw new RetryableFailure("resposta sem conteúdo");
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

    private void pause() {
        try {
            Thread.sleep(retryPause.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "O serviço de IA está instável agora. Tente de novo em instantes.", e);
        }
    }

    /** Falha que vale uma nova tentativa. */
    private static final class RetryableFailure extends RuntimeException {
        RetryableFailure(String message) {
            super(message);
        }
    }
}
