package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Cobre os casos de aceitação do filtro sem precisar de banco nem de contexto Spring. */
class BotAuthenticationFilterTest {

    private static final String KEY = "chave-secreta";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest botRequest(String key, String discordId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/badges");
        if (key != null) request.addHeader("X-Bot-Key", key);
        if (discordId != null) request.addHeader("X-Discord-Id", discordId);
        return request;
    }

    private User linkedUser(String discordId) {
        User user = new User();
        user.setEmail("user@myrank.dev");
        user.setDiscordId(discordId);
        return user;
    }

    @Test
    void keyValidaComDiscordVinculado_autentica_eExpoeOAtributo() throws Exception {
        when(userRepository.findByDiscordId("123")).thenReturn(Optional.of(linkedUser("123")));
        BotAuthenticationFilter filter = new BotAuthenticationFilter(userRepository, KEY);

        MockHttpServletRequest request = botRequest(KEY, "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(BotAuthenticationFilter.DISCORD_ID_ATTRIBUTE)).isEqualTo("123");
    }

    @Test
    void keyInvalida_401_eNaoSegueACadeia() throws Exception {
        BotAuthenticationFilter filter = new BotAuthenticationFilter(userRepository, KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(botRequest("errada", "123"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void semDiscordId_401() throws Exception {
        BotAuthenticationFilter filter = new BotAuthenticationFilter(userRepository, KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(botRequest(KEY, null), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void discordNaoVinculado_401_comMensagemAcionavel() throws Exception {
        when(userRepository.findByDiscordId(anyString())).thenReturn(Optional.empty());
        BotAuthenticationFilter filter = new BotAuthenticationFilter(userRepository, KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(botRequest(KEY, "999"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("não está vinculada");
        verify(chain, never()).doFilter(any(), any());
    }

    /** Deploy sem MYRANK_BOT_API_KEY nunca pode virar bypass: o filtro se desativa. */
    @Test
    void keyNaoConfigurada_filtroInerte() throws Exception {
        BotAuthenticationFilter filter = new BotAuthenticationFilter(userRepository, "");
        MockHttpServletRequest request = botRequest(KEY, "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(BotAuthenticationFilter.DISCORD_ID_ATTRIBUTE)).isNull();
        verifyNoInteractions(userRepository);
    }

    /** Fora do conjunto fechado de rotas a key é ignorada — exige JWT normal. */
    @Test
    void rotaForaDoEscopo_keyIgnorada() throws Exception {
        BotAuthenticationFilter filter = new BotAuthenticationFilter(userRepository, KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/social/takes");
        request.addHeader("X-Bot-Key", KEY);
        request.addHeader("X-Discord-Id", "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userRepository);
    }
}
