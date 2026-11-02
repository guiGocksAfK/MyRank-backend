package br.com.myrank.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitEncodedPathTest {

    @Test
    void caminhoCodificado_contaNoMesmoLimiteDoLogin() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 10; i++) {
            filter.doFilter(login("/api/auth/login"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse encoded = new MockHttpServletResponse();
        filter.doFilter(login("/api/auth/log%69n"), encoded, chain);

        assertThat(encoded.getStatus()).isEqualTo(429);
    }

    private static MockHttpServletRequest login(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }
}
