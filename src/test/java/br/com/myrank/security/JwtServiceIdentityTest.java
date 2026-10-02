package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceIdentityTest {

    private static final String SECRET = "test-only-secret-nao-usar-em-lugar-nenhum-de-verdade-1234567890";

    private static JwtService service() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", SECRET);
        ReflectionTestUtils.setField(service, "expirationMs", 3_600_000L);
        return service;
    }

    private static User user(long id, int tokenVersion) {
        User user = new User();
        user.setId(id);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    @Test
    void tokenIsBoundToImmutableUserId() {
        JwtService service = service();

        String token = service.generateToken(user(42L, 0));

        assertThat(service.extractUserId(token)).isEqualTo(42L);
        assertThat(service.isTokenValid(token, user(42L, 0))).isTrue();
        assertThat(service.isTokenValid(token, user(43L, 0))).isFalse();

        String previousFormat = Jwts.builder().subject("old@example.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        "test-only-secret-nao-usar-em-lugar-nenhum-de-verdade-1234567890"
                                .getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> service.extractUserId(previousFormat))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void trocarAVersaoDaConta_derrubaOsTokensAntigos() {
        JwtService service = service();
        String old = service.generateToken(user(42L, 0));

        assertThat(service.isTokenValid(old, user(42L, 1))).isFalse();
        assertThat(service.isTokenValid(service.generateToken(user(42L, 1)), user(42L, 1))).isTrue();
    }

    @Test
    void tokenDeAntesDaV16_semVersao_valeComoZero() {
        JwtService service = service();
        String legacy = Jwts.builder().subject("42")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(service.isTokenValid(legacy, user(42L, 0))).isTrue();
        assertThat(service.isTokenValid(legacy, user(42L, 1))).isFalse();
    }
}
