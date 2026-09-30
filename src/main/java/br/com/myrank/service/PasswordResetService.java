package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.auth.ForgotPasswordResponseDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * "Esqueci minha senha": link de uso único por email, válido por 15 minutos.
 * Mesmo padrão da confirmação de email: o token vai só no email e no banco fica
 * o SHA-256 dele, então um vazamento do banco não permite trocar senhas.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final BrevoEmailClient emailClient;
    private final PasswordEncoder passwordEncoder;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                BrevoEmailClient emailClient,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.emailClient = emailClient;
        this.passwordEncoder = passwordEncoder;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /**
     * Conta com senha: gera um token novo (o anterior deixa de valer) e manda o link.
     * Conta que só entra pelo Google/Discord (sem senha): avisa na hora qual provedor
     * usar, sem mandar email — decisão de produto, aceitando que isso revela que o
     * email tem conta social. Email sem conta: mesma resposta das contas com senha.
     */
    public ForgotPasswordResponseDTO request(String email) {
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            return ForgotPasswordResponseDTO.sent();
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            return ForgotPasswordResponseDTO.social(user.getAuthProvider().name());
        }
        issueAndSend(user);
        return ForgotPasswordResponseDTO.sent();
    }

    /** Troca a senha da conta dona do token e invalida o token. */
    public void reset(String token, String newPassword) {
        User user = userRepository.findByPasswordResetTokenHash(sha256(token.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Link de redefinição inválido ou já usado."));

        if (user.getPasswordResetExpiresAt() == null
                || user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Link de redefinição expirado. Peça um novo.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        // Quem abriu o link provou que é dono do email.
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    private void issueAndSend(User user) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        user.setPasswordResetTokenHash(sha256(token));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plus(TOKEN_TTL));
        userRepository.save(user);

        String link = frontendUrl + "/redefinir-senha?token=" + token;

        if (!emailClient.isConfigured()) {
            log.warn("BREVO_API_KEY/MAIL_FROM_EMAIL não configurados — redefinição de senha indisponível para o usuário {}",
                    user.getId());
            return;
        }

        try {
            EmailText text = EmailText.of(user.getLanguage());
            emailClient.send(user.getEmail(), text.subject(), text.html(user.getUsername(), link));
        } catch (RestClientException ex) {
            log.error("Falha ao enviar email de redefinição de senha do usuário {}: {}", user.getId(), ex.getMessage());
        }
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
                        "Reset your MyRank password",
                        "Hi, %s!",
                        "We got a request to reset your MyRank password. Click the button below to choose a new one.",
                        "Choose a new password",
                        "The link expires in 15 minutes and can be used once. If you didn't ask for this, just ignore this email: your password stays the same.");
                case "ES" -> new EmailText(
                        "Restablece tu contraseña de MyRank",
                        "¡Hola, %s!",
                        "Recibimos un pedido para restablecer tu contraseña de MyRank. Haz clic en el botón de abajo para elegir una nueva.",
                        "Elegir nueva contraseña",
                        "El enlace caduca en 15 minutos y se puede usar una vez. Si no lo pediste, ignora este email: tu contraseña sigue igual.");
                default -> new EmailText(
                        "Redefina sua senha do MyRank",
                        "Olá, %s!",
                        "Recebemos um pedido para redefinir sua senha do MyRank. Clique no botão abaixo para escolher uma nova.",
                        "Escolher nova senha",
                        "O link expira em 15 minutos e só pode ser usado uma vez. Se você não pediu isso, é só ignorar este email: sua senha continua a mesma.");
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
