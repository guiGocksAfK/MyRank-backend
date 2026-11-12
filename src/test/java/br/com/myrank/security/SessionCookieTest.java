package br.com.myrank.security;

import br.com.myrank.dto.auth.LoginResponseDTO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookieTest {

    private static final String FRONT = "https://app.myrank.dev";

    private SessionCookie cookie(String domain) {
        return new SessionCookie(true, "Strict", domain, 86_400_000L, "http://localhost:5173, " + FRONT);
    }

    @Test
    void entrou_gravaCookieHttpOnlySecureStrict_eMantemOTokenNoCorpo() {
        ResponseEntity<LoginResponseDTO> response = cookie("").signedIn(new LoginResponseDTO("tok", "gui"));

        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(SessionCookie.NAME + "=tok")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/", "Max-Age=86400")
                .doesNotContain("Domain=");
        // o front atual ainda lê o token do corpo durante a transição
        assertThat(response.getBody().token()).isEqualTo("tok");
    }

    @Test
    void sair_apagaComValidadeZero_noMesmoDominio() {
        String header = cookie("myrank.dev").clearHeader();

        assertThat(header).startsWith(SessionCookie.NAME + "=;")
                .contains("Max-Age=0", "Domain=myrank.dev", "Path=/", "HttpOnly");
    }

    @Test
    void le_soOCookieDaSessao_eIgnoraVazio() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(cookie("").read(request)).isNull();

        request.setCookies(new Cookie("outro", "x"), new Cookie(SessionCookie.NAME, " "));
        assertThat(cookie("").read(request)).isNull();

        request.setCookies(new Cookie("outro", "x"), new Cookie(SessionCookie.NAME, "tok"));
        assertThat(cookie("").read(request)).isEqualTo("tok");
    }

    @Test
    void origemConfiavel_soAsDaListaDoCors() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/works");
        assertThat(cookie("").isTrustedOrigin(request)).isFalse(); // sem Origin

        request.addHeader(HttpHeaders.ORIGIN, "https://site-malicioso.com");
        assertThat(cookie("").isTrustedOrigin(request)).isFalse();

        MockHttpServletRequest front = new MockHttpServletRequest("POST", "/api/works");
        front.addHeader(HttpHeaders.ORIGIN, FRONT);
        assertThat(cookie("").isTrustedOrigin(front)).isTrue();
    }
}
