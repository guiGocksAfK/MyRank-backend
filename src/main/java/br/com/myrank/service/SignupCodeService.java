package br.com.myrank.service;

import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.service.email.EmailLayout;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primeira etapa do cadastro com senha: prova que a pessoa é dona do email ANTES
 * de existir qualquer conta. Manda um código de 6 dígitos; acertando, devolve um
 * passe assinado que o cadastro final exige.
 *
 * Os códigos ficam só em memória (hash SHA-256, 15 minutos, 5 tentativas): um
 * reinício do servidor apaga os pendentes e a pessoa só pede outro. O passe é um
 * JWT com chave derivada da do login, então não serve como sessão e vice-versa.
 */
@Service
public class SignupCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final Duration PASS_TTL = Duration.ofMinutes(30);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 5;
    /** Teto de memória: acima disso, descarta os expirados antes de aceitar outro. */
    private static final int MAX_PENDING = 10_000;

    private record Pending(String codeHash, Instant sentAt, Instant expiresAt, int attempts) {}

    private final UserRepository userRepository;
    private final BrevoEmailClient emailClient;
    private final SecretKey passKey;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public SignupCodeService(UserRepository userRepository,
                             BrevoEmailClient emailClient,
                             @Value("${jwt.secret:}") String jwtSecret) {
        this(userRepository, emailClient, jwtSecret, Clock.systemUTC());
    }

    SignupCodeService(UserRepository userRepository, BrevoEmailClient emailClient, String jwtSecret, Clock clock) {
        this.userRepository = userRepository;
        this.emailClient = emailClient;
        this.passKey = Keys.hmacShaKeyFor(sha256Bytes("signup-pass:" + jwtSecret));
        this.clock = clock;
    }

    /**
     * Manda (ou reenvia) o código. Um código novo derruba o anterior. Email de conta
     * já confirmada é recusado na hora: o cadastro antigo já dizia isso, e poupa a
     * pessoa de esperar um código que não levaria a lugar nenhum.
     */
    public void sendCode(String email, String language) {
        String key = normalize(email);
        if (emailTaken(email.trim())) {
            throw new IllegalArgumentException("Email já está em uso.");
        }
        if (!emailClient.isConfigured()) {
            throw new IllegalStateException("Envio de email indisponível. Tente novamente mais tarde.");
        }

        Instant now = clock.instant();
        Pending previous = pending.get(key);
        if (previous != null && previous.sentAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            throw new IllegalArgumentException("Aguarde alguns segundos antes de pedir outro código.");
        }
        if (pending.size() >= MAX_PENDING) {
            pending.values().removeIf(p -> !p.expiresAt().isAfter(now));
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        CodeText text = CodeText.of(language);
        emailClient.send(email.trim(), text.subject(), text.html(code));
        pending.put(key, new Pending(sha256(code), now, now.plus(CODE_TTL), 0));
    }

    /** Confere o código e devolve o passe do cadastro. O código só vale uma vez. */
    public String verifyCode(String email, String code) {
        String key = normalize(email);
        Instant now = clock.instant();
        Pending entry = pending.get(key);
        if (entry == null || !entry.expiresAt().isAfter(now)) {
            pending.remove(key);
            throw new IllegalArgumentException("Código expirado. Peça um novo.");
        }

        String supplied = code == null ? "" : code.replaceAll("\\s", "");
        boolean matches = MessageDigest.isEqual(
                entry.codeHash().getBytes(StandardCharsets.US_ASCII),
                sha256(supplied).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int attempts = entry.attempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                pending.remove(key);
                throw new IllegalArgumentException("Muitas tentativas. Peça um novo código.");
            }
            pending.put(key, new Pending(entry.codeHash(), entry.sentAt(), entry.expiresAt(), attempts));
            throw new IllegalArgumentException("Código incorreto.");
        }

        pending.remove(key);
        return Jwts.builder()
                .subject(email.trim())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(PASS_TTL)))
                .signWith(passKey)
                .compact();
    }

    /** Email que o passe comprova. Passe inválido ou vencido: volta pro começo. */
    public String emailFromPass(String pass) {
        try {
            return Jwts.parser()
                    .verifyWith(passKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(pass)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("A confirmação do email expirou. Comece o cadastro de novo.");
        }
    }

    /** Conta com senha nunca confirmada (fluxo antigo) não prende o email. */
    private boolean emailTaken(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> user.getAuthProvider() != AuthProvider.LOCAL || user.isEmailVerified())
                .isPresent();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
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
