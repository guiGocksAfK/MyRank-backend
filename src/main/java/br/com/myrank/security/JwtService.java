package br.com.myrank.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    /** HS256 exige chave de 256 bits — 32 bytes ASCII é o mínimo absoluto. */
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret:}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expirationMs;

    /**
     * Falha o boot se o segredo não foi configurado (ou é curto demais). Sem isso
     * a app rodava assinando token com o placeholder versionado no repo — qualquer
     * um forjaria um JWT válido pra qualquer conta.
     */
    @PostConstruct
    void validateSecret() {
        if (secretKey == null || secretKey.isBlank() || secretKey.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET não configurado ou com menos de " + MIN_SECRET_LENGTH
                    + " caracteres. Gere um segredo aleatório e defina a env var JWT_SECRET "
                    + "(ex.: `openssl rand -base64 48`).");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return extractedUsername.equals(username) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}