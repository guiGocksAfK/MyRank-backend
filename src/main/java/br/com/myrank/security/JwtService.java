package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
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
    /** Versão de sessão da conta no momento do login (ver User#tokenVersion). */
    private static final String VERSION_CLAIM = "ver";

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

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(extractClaim(token, Claims::getSubject));
    }

    /**
     * Token é da conta, não venceu e tem a versão de sessão atual dela. Token sem
     * versão (emitido antes da V16) conta como 0.
     */
    public boolean isTokenValid(String token, User user) {
        Claims claims = parse(token);
        Integer version = claims.get(VERSION_CLAIM, Integer.class);
        return claims.getSubject().equals(user.getId().toString())
                && claims.getExpiration().after(new Date())
                && (version == null ? 0 : version) == user.getTokenVersion();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parse(token));
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
