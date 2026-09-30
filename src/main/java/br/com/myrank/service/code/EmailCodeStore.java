package br.com.myrank.service.code;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Códigos de 6 dígitos mandados por email, guardados só em memória (hash SHA-256).
 * Cada fluxo (cadastro, recuperação de senha) tem o seu. Um código novo derruba o
 * anterior; o código morre ao acertar, ao vencer ou depois de {@code maxAttempts}
 * erros. Reiniciar o servidor apaga os pendentes e a pessoa só pede outro.
 */
public final class EmailCodeStore {

    /** Teto de memória: acima disso, descarta os vencidos antes de aceitar outro. */
    private static final int MAX_PENDING = 10_000;

    private record Pending(String codeHash, Instant sentAt, Instant expiresAt, int attempts) {}

    private final Clock clock;
    private final Duration ttl;
    private final Duration resendCooldown;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public EmailCodeStore(Clock clock, Duration ttl, Duration resendCooldown, int maxAttempts) {
        this.clock = clock;
        this.ttl = ttl;
        this.resendCooldown = resendCooldown;
        this.maxAttempts = maxAttempts;
    }

    /** Gera o código do email (o anterior deixa de valer) e devolve pra quem vai mandar. */
    public String issue(String email) {
        String key = key(email);
        Instant now = clock.instant();
        Pending previous = pending.get(key);
        if (previous != null && previous.sentAt().plus(resendCooldown).isAfter(now)) {
            throw new IllegalArgumentException("Aguarde alguns segundos antes de pedir outro código.");
        }
        if (pending.size() >= MAX_PENDING) {
            pending.values().removeIf(p -> !p.expiresAt().isAfter(now));
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        pending.put(key, new Pending(sha256(code), now, now.plus(ttl), 0));
        return code;
    }

    /** Desfaz um {@link #issue} cujo email não chegou a sair, pra não travar o reenvio. */
    public void discard(String email) {
        pending.remove(key(email));
    }

    /** Confere o código; acertando, ele é consumido. Errando, lança com a mensagem pra tela. */
    public void verify(String email, String code) {
        String key = key(email);
        Pending entry = pending.get(key);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            pending.remove(key);
            throw new IllegalArgumentException("Código expirado. Peça um novo.");
        }

        String supplied = code == null ? "" : code.replaceAll("\\s", "");
        boolean matches = MessageDigest.isEqual(
                entry.codeHash().getBytes(StandardCharsets.US_ASCII),
                sha256(supplied).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int attempts = entry.attempts() + 1;
            if (attempts >= maxAttempts) {
                pending.remove(key);
                throw new IllegalArgumentException("Muitas tentativas. Peça um novo código.");
            }
            pending.put(key, new Pending(entry.codeHash(), entry.sentAt(), entry.expiresAt(), attempts));
            throw new IllegalArgumentException("Código incorreto.");
        }

        pending.remove(key);
    }

    private static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }
}
