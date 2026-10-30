package br.com.myrank.service.ai;

import br.com.myrank.dto.insight.InsightPayloadDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra um servidor HTTP local que imita o endpoint compatível com a OpenAI. */
class GeminiClientTest {

    private static final String VALID_PAYLOAD = """
            {"summaryTitle":"T","summaryText":"S","traits":[{"icon":"x","label":"l","description":"d"}],
             "tasteProfile":[{"name":"n","percent":50}],
             "recommendation":{"title":"R","year":2000,"category":"Filme","compatPercent":80,"reason":"r"}}""";

    private record Reply(int status, String body) {}

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<String> requests = new ArrayList<>();
    private HttpServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Reply reply = replies.isEmpty() ? new Reply(500, "{}") : replies.poll();
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new GeminiClient(objectMapper, "chave", baseUrl, "gemini-2.5-flash", "none", 0);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String completion(String content) throws IOException {
        return objectMapper.writeValueAsString(
                java.util.Map.of("choices", List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
    }

    @Test
    void analise_mandaRaciocinioDesligado() throws IOException {
        replies.add(new Reply(200, completion(VALID_PAYLOAD)));

        InsightPayloadDTO payload = client.analyze("sys", "user");

        assertThat(payload.summaryTitle()).isEqualTo("T");
        assertThat(objectMapper.readTree(requests.get(0)).path("reasoning_effort").asText()).isEqualTo("none");
    }

    @Test
    void limiteDoPlanoGratuito_tentaDeNovoUmaVez() throws IOException {
        replies.add(new Reply(429, "{\"error\":\"quota\"}"));
        replies.add(new Reply(200, completion(VALID_PAYLOAD)));

        assertThat(client.analyze("sys", "user").recommendation().title()).isEqualTo("R");
        assertThat(requests).hasSize(2);
    }

    @Test
    void jsonQuebrado_tentaDeNovoUmaVez() throws IOException {
        replies.add(new Reply(200, completion("{\"summaryTitle\": \"T\", ")));
        replies.add(new Reply(200, completion("```json\n" + VALID_PAYLOAD + "\n```")));

        assertThat(client.analyze("sys", "user").summaryTitle()).isEqualTo("T");
        assertThat(requests).hasSize(2);
    }

    @Test
    void duasFalhas_desisteComMensagemClara() {
        replies.add(new Reply(503, "{}"));
        replies.add(new Reply(503, "{}"));

        assertThatThrownBy(() -> client.analyze("sys", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("O serviço de IA está instável agora. Tente gerar a análise de novo em instantes.");
        assertThat(requests).hasSize(2);
    }

    @Test
    void pedidoRecusado_naoRepete() {
        replies.add(new Reply(400, "{\"error\":\"bad request\"}"));

        assertThatThrownBy(() -> client.analyze("sys", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("O serviço de IA recusou o pedido. Tente gerar a análise de novo mais tarde.");
        assertThat(requests).hasSize(1);
    }

    @Test
    void chat_tentaDeNovoNaFalhaTemporaria() throws IOException {
        replies.add(new Reply(500, "{}"));
        replies.add(new Reply(200, completion("Resposta.")));

        assertThat(client.chat("sys", "ctx", List.of(), "pergunta?")).isEqualTo("Resposta.");
        assertThat(requests).hasSize(2);
    }

    @Test
    void semRaciocinioConfigurado_naoMandaOCampo() throws IOException {
        client = new GeminiClient(objectMapper, "chave",
                "http://127.0.0.1:" + server.getAddress().getPort(), "outro-modelo", "", 0);
        replies.add(new Reply(200, completion(VALID_PAYLOAD)));

        client.analyze("sys", "user");

        assertThat(objectMapper.readTree(requests.get(0)).has("reasoning_effort")).isFalse();
    }
}
