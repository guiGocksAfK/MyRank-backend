package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sessão pelo cookie: leitura sempre vale; escrita só com a origem do front (CSRF). */
class JwtAuthenticationFilterCookieTest {

    private static final String FRONT = "https://app.myrank.dev";

    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final SessionCookie sessionCookie =
            new SessionCookie(true, "Strict", "", 86_400_000L, FRONT);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, userRepository, sessionCookie);

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(7L);
        user.setEmail("gui@myrank.dev");
        when(jwtService.extractUserId("tok")).thenReturn(7L);
        when(jwtService.isTokenValid(eq("tok"), any(User.class))).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest withCookie(String method, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/works");
        request.setServletPath("/api/works");
        request.setCookies(new Cookie(SessionCookie.NAME, "tok"));
        if (origin != null) request.addHeader(HttpHeaders.ORIGIN, origin);
        return request;
    }

    private boolean authenticated(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Test
    void leituraComCookie_autentica_mesmoSemOrigin() throws Exception {
        assertThat(authenticated(withCookie("GET", null))).isTrue();
    }

    @Test
    void escritaComCookie_eOrigemDoFront_autentica() throws Exception {
        assertThat(authenticated(withCookie("POST", FRONT))).isTrue();
    }

    @Test
    void escritaComCookie_vindaDeOutroSite_naoAutentica() throws Exception {
        assertThat(authenticated(withCookie("DELETE", "https://site-malicioso.com"))).isFalse();
    }

    @Test
    void escritaComCookie_semOrigin_naoAutentica() throws Exception {
        assertThat(authenticated(withCookie("PUT", null))).isFalse();
    }

    /** Front antigo: o header não vai sozinho, então não é alvo de CSRF e dispensa a origem. */
    @Test
    void headerAuthorization_continuaValendo_semOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/works");
        request.setServletPath("/api/works");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer tok");

        assertThat(authenticated(request)).isTrue();
    }
}
