package br.com.myrank.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envio de email transacional pela API HTTP do Brevo
 * ({@code POST https://api.brevo.com/v3/smtp/email}). Sem chave configurada o
 * cliente fica desligado e quem chama decide o que fazer (ver EmailVerificationService).
 */
@Component
public class BrevoEmailClient {

    private static final String SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient http;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailClient(@Value("${brevo.api-key:}") String apiKey,
                            @Value("${mail.from-email:}") String fromEmail,
                            @Value("${mail.from-name:MyRank}") String fromName) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !fromEmail.isBlank();
    }

    /** Lança RestClientException se o Brevo recusar ou estiver fora do ar. */
    public void send(String to, String subject, String html) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", html
        );

        http.post()
                .uri(SEND_URL)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
