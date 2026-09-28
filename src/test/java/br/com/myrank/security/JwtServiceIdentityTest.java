package br.com.myrank.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceIdentityTest {
    @Test
    void tokenIsBoundToImmutableUserId() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", "test-only-secret-nao-usar-em-lugar-nenhum-de-verdade-1234567890");
        ReflectionTestUtils.setField(service, "expirationMs", 3_600_000L);

        String token = service.generateToken(42L);

        assertThat(service.extractUserId(token)).isEqualTo(42L);
        assertThat(service.isTokenValid(token, 42L)).isTrue();
        assertThat(service.isTokenValid(token, 43L)).isFalse();

        String previousFormat = Jwts.builder().subject("old@example.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        "test-only-secret-nao-usar-em-lugar-nenhum-de-verdade-1234567890"
                                .getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> service.extractUserId(previousFormat))
                .isInstanceOf(NumberFormatException.class);
    }
}
