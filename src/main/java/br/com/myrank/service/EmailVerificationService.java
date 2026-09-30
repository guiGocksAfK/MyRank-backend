package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.service.email.EmailLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
            log.warn("BREVO_API_KEY/MAIL_FROM_EMAIL não configurados — confirmação de email indisponível para o usuário {}",
                    user.getId());
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

    /** Texto do email no idioma da conta (PT | EN | ES); o visual vem do EmailLayout. */
    private record EmailText(String subject, String preheader, String greeting, String intro, String button,
                             String outro, String note, String fallbackLabel, String footer) {

        static EmailText of(String language) {
            return switch (language == null ? "PT" : language) {
                case "EN" -> new EmailText(
                        "One click and your ranking begins",
                        "Confirm your email and start building your tables.",
                        "Hi, %s!",
                        "Your MyRank is almost ready. We just need to confirm this email is yours.",
                        "Confirm my email",
                        "Then it's all yours: build your tables, give your scores and find out what they say about your taste.",
                        "The link is valid for 24 hours.",
                        "Button not working? Copy and paste this link into your browser:",
                        "You got this email because you created a MyRank account. If it wasn't you, just ignore it.");
                case "ES" -> new EmailText(
                        "Un clic y tu ranking empieza",
                        "Confirma tu email y empieza a armar tus tablas.",
                        "¡Hola, %s!",
                        "Tu MyRank está casi listo. Solo falta confirmar que este email es tuyo.",
                        "Confirmar mi email",
                        "Después es cosa tuya: arma tus tablas, pon tus notas y descubre lo que dicen de tu gusto.",
                        "El enlace vale por 24 horas.",
                        "¿El botón no funciona? Copia y pega este enlace en tu navegador:",
                        "Recibiste este email porque creaste una cuenta en MyRank. Si no fuiste tú, ignóralo.");
                default -> new EmailText(
                        "Falta um clique pro seu ranking começar",
                        "Confirme seu email e comece a montar suas tabelas.",
                        "Olá, %s!",
                        "Seu MyRank está quase pronto. Só falta confirmar que este email é seu.",
                        "Confirmar meu email",
                        "Depois é com você: monte suas tabelas, dê suas notas e descubra o que elas dizem sobre o seu gosto.",
                        "O link vale por 24 horas.",
                        "O botão não funcionou? Copie e cole este link no navegador:",
                        "Você recebeu este email porque criou uma conta no MyRank. Se não foi você, é só ignorar.");
            };
        }

        String html(String username, String link) {
            return EmailLayout.render(new EmailLayout.Content(
                    preheader, greeting.formatted(username), intro, button, link,
                    outro, note, fallbackLabel, footer));
        }
    }
}
