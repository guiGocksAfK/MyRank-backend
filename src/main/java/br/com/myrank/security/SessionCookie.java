package br.com.myrank.security;

import br.com.myrank.dto.auth.LoginResponseDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Sessão guardada num cookie {@code HttpOnly}: o JavaScript da página não consegue
 * ler o token, então um XSS no front não o rouba (no {@code localStorage} roubava).
 *
 * <p>Cookie vai sozinho em toda requisição, o que abre a porta pro CSRF. As duas
 * travas: {@code SameSite=Strict} (o navegador não manda o cookie em requisição
 * vinda de outro site) e a conferência da origem em {@link #isTrustedOrigin}.
 */
@Component
public class SessionCookie {

    public static final String NAME = "myrank_session";

    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final Duration maxAge;
    private final List<String> allowedOrigins;

    public SessionCookie(
            @Value("${auth.cookie.secure:true}") boolean secure,
            @Value("${auth.cookie.same-site:Strict}") String sameSite,
            @Value("${auth.cookie.domain:}") String domain,
            @Value("${jwt.expiration}") long expirationMs,
            @Value("${cors.allowed-origins:http://localhost:5173}") String allowedOrigins
    ) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = domain == null ? "" : domain.trim();
        this.maxAge = Duration.ofMillis(expirationMs);
        this.allowedOrigins = Arrays.asList(allowedOrigins.split("\\s*,\\s*"));
    }

    /** Resposta de "entrou": o corpo de sempre mais o cookie da sessão. */
    public ResponseEntity<LoginResponseDTO> signedIn(LoginResponseDTO login) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, build(login.token(), maxAge).toString())
                .body(login);
    }

    /** Cabeçalho que apaga o cookie no navegador (mesmo nome, caminho e domínio, validade zero). */
    public String clearHeader() {
        return build("", Duration.ZERO).toString();
    }

    /** Token do cookie da sessão, ou {@code null} se a requisição não trouxe. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Segunda trava contra CSRF, pra requisição que muda algo e veio autenticada
     * pelo cookie: a origem tem que ser a do front. Navegador sempre manda
     * {@code Origin} em POST/PUT/PATCH/DELETE; sem ele (ou com outro site), recusa.
     */
    public boolean isTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        return origin != null && allowedOrigins.contains(origin);
    }

    private ResponseCookie build(String value, Duration age) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(age);
        if (!domain.isEmpty()) cookie.domain(domain);
        return cookie.build();
    }
}
