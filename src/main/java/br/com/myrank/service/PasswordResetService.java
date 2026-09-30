package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.auth.ForgotPasswordResponseDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.service.email.EmailLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

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

    /** Texto do email no idioma da conta (PT | EN | ES); o visual vem do EmailLayout. */
    private record EmailText(String subject, String preheader, String greeting, String intro, String button,
                             String safetyTitle, String safetyText, String note,
                             String fallbackLabel, String footer) {

        static EmailText of(String language) {
            return switch (language == null ? "PT" : language) {
                case "EN" -> new EmailText(
                        "Your link to create a new password",
                        "The link is valid for 15 minutes.",
                        "Hi, %s!",
                        "We got a request to reset the password of your MyRank account. If it was you, just choose a new one.",
                        "Create new password",
                        "Wasn't you?",
                        "Your account is safe. Without clicking the button, nothing changes and your password stays the same.",
                        "The link is valid for 15 minutes and works only once.",
                        "Button not working? Copy and paste this link into your browser:",
                        "You got this email because someone asked to reset the password of your MyRank account.");
                case "ES" -> new EmailText(
                        "Tu enlace para crear una nueva contraseña",
                        "El enlace vale por 15 minutos.",
                        "¡Hola, %s!",
                        "Recibimos un pedido para restablecer la contraseña de tu cuenta de MyRank. Si fuiste tú, solo elige una nueva.",
                        "Crear nueva contraseña",
                        "¿No fuiste tú?",
                        "Tu cuenta está segura. Sin hacer clic en el botón, nada cambia y tu contraseña sigue igual.",
                        "El enlace vale por 15 minutos y funciona una sola vez.",
                        "¿El botón no funciona? Copia y pega este enlace en tu navegador:",
                        "Recibiste este email porque alguien pidió restablecer la contraseña de tu cuenta de MyRank.");
                default -> new EmailText(
                        "Seu link pra criar uma nova senha",
                        "O link vale por 15 minutos.",
                        "Olá, %s!",
                        "Recebemos um pedido pra redefinir a senha da sua conta no MyRank. Se foi você, é só escolher uma nova.",
                        "Criar nova senha",
                        "Não foi você?",
                        "Sua conta está segura. Sem clicar no botão, nada muda e sua senha continua a mesma.",
                        "O link vale por 15 minutos e só funciona uma vez.",
                        "O botão não funcionou? Copie e cole este link no navegador:",
                        "Você recebeu este email porque pediram a redefinição de senha da sua conta no MyRank.");
            };
        }

        String html(String username, String link) {
            return EmailLayout.render(new EmailLayout.Content(
                    preheader, greeting.formatted(username), intro, button, link,
                    null, safetyTitle, safetyText, note, fallbackLabel, footer));
        }
    }
}
