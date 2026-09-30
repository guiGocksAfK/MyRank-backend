package br.com.myrank.service;

import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.code.EmailCodeStore;
import br.com.myrank.service.code.EmailPass;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.service.email.EmailLayout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Primeira etapa do cadastro com senha: prova que a pessoa é dona do email ANTES
 * de existir qualquer conta. Manda um código de 6 dígitos (15 minutos, 5
 * tentativas); acertando, devolve um passe que o cadastro final exige.
 */
@Service
public class SignupCodeService {

    private final UserRepository userRepository;
    private final BrevoEmailClient emailClient;
    private final EmailCodeStore codes;
    private final EmailPass pass;

    public SignupCodeService(UserRepository userRepository,
                             BrevoEmailClient emailClient,
                             @Value("${jwt.secret:}") String jwtSecret,
                             Clock clock) {
        this.userRepository = userRepository;
        this.emailClient = emailClient;
        this.codes = new EmailCodeStore(clock, Duration.ofMinutes(15), Duration.ofSeconds(30), 5);
        this.pass = new EmailPass(jwtSecret, "signup-pass", Duration.ofMinutes(30), clock);
    }

    /**
     * Manda (ou reenvia) o código. Email de conta já confirmada é recusado na hora:
     * o cadastro antigo já dizia isso, e poupa a pessoa de esperar um código que
     * não levaria a lugar nenhum.
     */
    public void sendCode(String email, String language) {
        if (emailTaken(email.trim())) {
            throw new IllegalArgumentException("Email já está em uso.");
        }
        if (!emailClient.isConfigured()) {
            throw new IllegalStateException("Envio de email indisponível. Tente novamente mais tarde.");
        }

        String code = codes.issue(email);
        CodeText text = CodeText.of(language);
        try {
            emailClient.send(email.trim(), text.subject(), text.html(code));
        } catch (RestClientException ex) {
            codes.discard(email);
            throw ex;
        }
    }

    /** Confere o código e devolve o passe do cadastro. O código só vale uma vez. */
    public String verifyCode(String email, String code) {
        codes.verify(email, code);
        return pass.issue(email);
    }

    /** Email que o passe comprova. Passe inválido ou vencido: volta pro começo. */
    public String emailFromPass(String signupPass) {
        return pass.emailFrom(signupPass, "A confirmação do email expirou. Comece o cadastro de novo.");
    }

    /** Conta com senha nunca confirmada (fluxo antigo) não prende o email. */
    private boolean emailTaken(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> user.getAuthProvider() != AuthProvider.LOCAL || user.isEmailVerified())
                .isPresent();
    }

    /** Email do código (PT | EN | ES); o visual vem do EmailLayout. */
    private record CodeText(String subject, String preheader, String greeting, String intro,
                            String outro, String safetyTitle, String safetyText, String note, String footer) {

        static CodeText of(String language) {
            String lang = language == null ? "PT" : language.trim().toUpperCase(Locale.ROOT);
            return switch (lang) {
                case "EN" -> new CodeText(
                        "Your MyRank code",
                        "Type it on the sign-up screen to continue.",
                        "Hi!",
                        "Here is the code to confirm your email and continue creating your MyRank account:",
                        "Then just pick your username and password, and your ranking begins.",
                        "Didn't try to sign up?",
                        "Relax and just ignore this email. Without this code, no account is created with your address.",
                        "The code is valid for 15 minutes.",
                        "You got this email because this address was used to start a MyRank sign-up.");
                case "ES" -> new CodeText(
                        "Tu código de MyRank",
                        "Escríbelo en la pantalla de registro para continuar.",
                        "¡Hola!",
                        "Este es el código para confirmar tu email y seguir creando tu cuenta de MyRank:",
                        "Después solo elige tu nombre de usuario y contraseña, y tu ranking empieza.",
                        "¿No intentaste registrarte?",
                        "Tranquilo, ignora este email. Sin este código, no se crea ninguna cuenta con tu dirección.",
                        "El código vale por 15 minutos.",
                        "Recibiste este email porque esta dirección se usó para empezar un registro en MyRank.");
                default -> new CodeText(
                        "Seu código do MyRank",
                        "Digite na tela de cadastro para continuar.",
                        "Olá!",
                        "Aqui está o código pra confirmar seu email e continuar criando sua conta no MyRank:",
                        "Depois é só escolher seu nome de usuário e sua senha, e o seu ranking começa.",
                        "Não tentou se cadastrar?",
                        "Pode ficar tranquilo e ignorar este email. Sem esse código, nenhuma conta é criada com o seu endereço.",
                        "O código vale por 15 minutos.",
                        "Você recebeu este email porque este endereço foi usado pra começar um cadastro no MyRank.");
            };
        }

        String html(String code) {
            return EmailLayout.render(new EmailLayout.Content(
                    preheader, greeting, intro, null, null, code,
                    outro, safetyTitle, safetyText, note, null, footer));
        }
    }
}
