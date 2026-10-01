package br.com.myrank.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExternalMusicRateLimitFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/external/search/music", "/api/external/search/albums",
            "/api/external/music/42", "/api/external/albums/50"})
    void novasRotasCompartilhamLimiteDeQuarentaComOutrasIntegracoes(String route) throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 39; i++) {
            filter.doFilter(request("/api/external/search/games", "127.0.0.1"),
                    new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse allowed = new MockHttpServletResponse();
        filter.doFilter(request(route, "127.0.0.1"), allowed, chain);
        assertThat(allowed.getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request(route, "127.0.0.1"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        verify(chain, times(40)).doFilter(any(), any());

        MockHttpServletResponse otherClient = new MockHttpServletResponse();
        filter.doFilter(request(route, "127.0.0.2"), otherClient, chain);
        assertThat(otherClient.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(ip);
        return request;
    }
}
