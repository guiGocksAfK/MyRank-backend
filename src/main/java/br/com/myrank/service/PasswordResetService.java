package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.auth.ForgotPasswordResponseDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.code.EmailCodeStore;
import br.com.myrank.service.code.EmailPass;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.service.email.EmailLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;

/**
 * "Esqueci minha senha" em três passos na mesma tela: código de 6 dígitos por
 * email (15 minutos, 5 tentativas) → passe curto → senha nova, já entrando na conta.
 *
 * Email sem conta segue o mesmo caminho de uma conta com senha (um código é gerado,
 * só não é mandado), então nem a resposta nem as mensagens de erro revelam quais
 * emails estão cadastrados.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final BrevoEmailClient emailClient;
    private final PasswordEncoder passwordEncoder;
    private final EmailCodeStore codes;
    private final EmailPass pass;

    public PasswordResetService(UserRepository userRepository,
                                BrevoEmailClient emailClient,
                                PasswordEncoder passwordEncoder,
                                @Value("${jwt.secret:}") String jwtSecret,
                                Clock clock) {
        this.userRepository = userRepository;
        this.emailClient = emailClient;
        this.passwordEncoder = passwordEncoder;
        this.codes = new EmailCodeStore(clock, Duration.ofMinutes(15), Duration.ofSeconds(30), 5);
        this.pass = new EmailPass(jwtSecret, "reset-pass", Duration.ofMinutes(15), clock);
    }

    /**
     * Conta com senha: manda o código (o anterior deixa de valer). Conta que só
     * entra pelo Google/Discord: avisa na hora qual provedor usar, sem email —
     * decisão de produto, aceitando que isso revela que o email tem conta social.
     */
    public ForgotPasswordResponseDTO request(String email) {
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user != null && !hasPassword(user)) {
            return ForgotPasswordResponseDTO.social(user.getAuthProvider().name());
        }

        String code = codes.issue(email);
        if (user != null) {
            send(user, code);
        }
        return ForgotPasswordResponseDTO.sent();
    }

    /** Confere o código e devolve o passe que libera a troca de senha. */
    public String verifyCode(String email, String code) {
        codes.verify(email, code);
        return pass.issue(email);
    }

    /** Troca a senha da conta do passe e devolve a conta, pra quem chama já abrir a sessão. */
    public User reset(String resetPass, String newPassword) {
        String email = pass.emailFrom(resetPass, "O código expirou. Peça um novo.");
        User user = userRepository.findByEmail(email)
                .filter(this::hasPassword)
                .orElseThrow(() -> new IllegalArgumentException("O código expirou. Peça um novo."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // restos do fluxo antigo por link (colunas da V15), se houver
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        // quem acertou o código provou que é dono do email
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private boolean hasPassword(User user) {
        return user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
    }

    /** Falha no envio só é logada: a tela é a mesma e a pessoa pode pedir de novo. */
    private void send(User user, String code) {
        if (!emailClient.isConfigured()) {
            log.warn("BREVO_API_KEY/MAIL_FROM_EMAIL não configurados — redefinição de senha indisponível para o usuário {}",
                    user.getId());
            return;
        }
        try {
            EmailText text = EmailText.of(user.getLanguage());
            emailClient.send(user.getEmail(), text.subject(), text.html(user.getUsername(), code));
        } catch (RestClientException ex) {
            codes.discard(user.getEmail());
            log.error("Falha ao enviar email de redefinição de senha do usuário {}: {}", user.getId(), ex.getMessage());
        }
    }

    /** Texto do email no idioma da conta (PT | EN | ES); o visual vem do EmailLayout. */
    private record EmailText(String subject, String preheader, String greeting, String intro,
                             String safetyTitle, String safetyText, String note, String footer) {

        static EmailText of(String language) {
            return switch (language == null ? "PT" : language) {
                case "EN" -> new EmailText(
                        "Your code to create a new password",
                        "The code is valid for 15 minutes.",
                        "Hi, %s!",
                        "We got a request to reset the password of your MyRank account. If it was you, type this code on the screen:",
                        "Wasn't you?",
                        "Your account is safe. Without this code, nothing changes and your password stays the same.",
                        "The code is valid for 15 minutes and works only once.",
                        "You got this email because someone asked to reset the password of your MyRank account.");
                case "ES" -> new EmailText(
                        "Tu código para crear una nueva contraseña",
                        "El código vale por 15 minutos.",
                        "¡Hola, %s!",
                        "Recibimos un pedido para restablecer la contraseña de tu cuenta de MyRank. Si fuiste tú, escribe este código en la pantalla:",
                        "¿No fuiste tú?",
                        "Tu cuenta está segura. Sin este código, nada cambia y tu contraseña sigue igual.",
                        "El código vale por 15 minutos y funciona una sola vez.",
                        "Recibiste este email porque alguien pidió restablecer la contraseña de tu cuenta de MyRank.");
                default -> new EmailText(
                        "Seu código pra criar uma nova senha",
                        "O código vale por 15 minutos.",
                        "Olá, %s!",
                        "Recebemos um pedido pra redefinir a senha da sua conta no MyRank. Se foi você, digite este código na tela:",
                        "Não foi você?",
                        "Sua conta está segura. Sem esse código, nada muda e sua senha continua a mesma.",
                        "O código vale por 15 minutos e só funciona uma vez.",
                        "Você recebeu este email porque pediram a redefinição de senha da sua conta no MyRank.");
            };
        }

        String html(String username, String code) {
            return EmailLayout.render(new EmailLayout.Content(
                    preheader, greeting.formatted(username), intro, null, null, code,
                    null, safetyTitle, safetyText, note, null, footer));
        }
    }
}
