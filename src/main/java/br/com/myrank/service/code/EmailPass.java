package br.com.myrank.service.code;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Passe curto que prova "este email acertou o código", pra liberar a etapa
 * seguinte (criar a conta, trocar a senha). É um JWT com chave derivada do
 * segredo do login e do propósito: não serve como sessão, e o passe de um fluxo
 * não vale no outro.
 */
public final class EmailPass {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public EmailPass(String jwtSecret, String purpose, Duration ttl, Clock clock) {
        this.key = Keys.hmacShaKeyFor(sha256(purpose + ":" + jwtSecret));
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(String email) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(email.trim())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Email que o passe comprova. Inválido ou vencido: lança com a mensagem pra tela. */
    public String emailFrom(String pass, String invalidMessage) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(pass)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(invalidMessage);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }
}
