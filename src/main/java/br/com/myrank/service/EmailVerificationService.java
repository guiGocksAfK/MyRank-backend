package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Link de confirmação de email do cadastro com senha. O token vai só no email;
 * no banco fica o SHA-256 dele, então um vazamento do banco não confirma contas.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final BrevoEmailClient emailClient;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository userRepository,
                                    BrevoEmailClient emailClient,
                                    @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.emailClient = emailClient;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /**
     * Gera um token novo (o anterior deixa de valer) e manda o link. Falha no envio
     * só é logada: a conta já existe e o usuário pode pedir reenvio pela tela.
     */
    public void issueAndSend(User user) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        user.setEmailVerificationTokenHash(sha256(token));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plus(TOKEN_TTL));
        userRepository.save(user);

        String link = frontendUrl + "/confirmar-email?token=" + token;

        if (!emailClient.isConfigured()) {
            // Dev local sem Brevo: o link sai no log pra dar pra testar o fluxo.
            log.warn("BREVO_API_KEY/MAIL_FROM_EMAIL não configurados — link de confirmação do usuário {}: {}",
                    user.getId(), link);
            return;
        }

        try {
            EmailText text = EmailText.of(user.getLanguage());
            emailClient.send(user.getEmail(), text.subject(), text.html(user.getUsername(), link));
        } catch (RestClientException ex) {
            log.error("Falha ao enviar email de confirmação do usuário {}: {}", user.getId(), ex.getMessage());
        }
    }

    /** Confirma a conta dona do token e invalida o token. */
    public User verify(String token) {
        User user = userRepository.findByEmailVerificationTokenHash(sha256(token.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Link de confirmação inválido ou já usado."));

        if (user.getEmailVerificationExpiresAt() == null
                || user.getEmailVerificationExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Link de confirmação expirado. Peça um novo na tela de login.");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationExpiresAt(null);
        return userRepository.save(user);
    }

    /**
     * Reenvia o link se existir uma conta com senha ainda não confirmada. Não diz
     * se o email existe — quem chama responde igual nos dois casos.
     */
    public void resend(String email) {
        userRepository.findByEmail(email.trim())
                .filter(user -> user.getAuthProvider() == AuthProvider.LOCAL && !user.isEmailVerified())
                .ifPresent(this::issueAndSend);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    /** Texto do email no idioma da conta (PT | EN | ES). */
    private record EmailText(String subject, String greeting, String body, String button, String footer) {

        static EmailText of(String language) {
            return switch (language == null ? "PT" : language) {
                case "EN" -> new EmailText(
                        "Confirm your MyRank email",
                        "Hi, %s!",
                        "Click the button below to confirm your email and activate your MyRank account.",
                        "Confirm email",
                        "The link expires in 24 hours. If you didn't create this account, just ignore this email.");
                case "ES" -> new EmailText(
                        "Confirma tu email de MyRank",
                        "¡Hola, %s!",
                        "Haz clic en el botón de abajo para confirmar tu email y activar tu cuenta de MyRank.",
                        "Confirmar email",
                        "El enlace caduca en 24 horas. Si no creaste esta cuenta, ignora este email.");
                default -> new EmailText(
                        "Confirme seu email no MyRank",
                        "Olá, %s!",
                        "Clique no botão abaixo para confirmar seu email e ativar sua conta no MyRank.",
                        "Confirmar email",
                        "O link expira em 24 horas. Se você não criou esta conta, é só ignorar este email.");
            };
        }

        String html(String username, String link) {
            String safeLink = HtmlUtils.htmlEscape(link);
            return """
                    <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#1a1a1a">
                      <h2 style="margin:0 0 16px">My<span style="color:#d4af37">Rank</span></h2>
                      <p style="font-size:16px">%s</p>
                      <p style="font-size:15px;line-height:1.5">%s</p>
                      <p style="margin:28px 0">
                        <a href="%s" style="background:#d4af37;color:#111;padding:12px 22px;border-radius:8px;text-decoration:none;font-weight:bold">%s</a>
                      </p>
                      <p style="font-size:13px;color:#666;line-height:1.5">%s</p>
                      <p style="font-size:12px;color:#999;word-break:break-all">%s</p>
                    </div>
                    """.formatted(
                    greeting.formatted(HtmlUtils.htmlEscape(username)),
                    body, safeLink, button, footer, safeLink);
        }
    }
}
