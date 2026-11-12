package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Chat em tempo real com a sessão no cookie: o handshake lê, o CONNECT confere. */
class ChatCookieAuthTest {

    private final SessionCookie sessionCookie =
            new SessionCookie(true, "Strict", "", 86_400_000L, "https://app.myrank.dev");
    private final SessionCookieHandshakeInterceptor handshake = new SessionCookieHandshakeInterceptor(sessionCookie);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StompAuthChannelInterceptor stomp = new StompAuthChannelInterceptor(jwtService, userRepository);

    private Map<String, Object> handshakeWith(Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/123/abc/websocket");
        if (cookies.length > 0) request.setCookies(cookies);
        Map<String, Object> attributes = new HashMap<>();
        boolean open = handshake.beforeHandshake(new ServletServerHttpRequest(request), null, null, attributes);
        assertThat(open).isTrue(); // sem cookie abre mesmo assim; quem recusa é o CONNECT
        return attributes;
    }

    private Message<byte[]> connect(Map<String, Object> sessionAttributes) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionAttributes(sessionAttributes);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void handshake_guardaOTokenDoCookie() {
        assertThat(handshakeWith(new Cookie(SessionCookie.NAME, "tok")))
                .containsEntry(SessionCookieHandshakeInterceptor.TOKEN_ATTR, "tok");
        assertThat(handshakeWith()).doesNotContainKey(SessionCookieHandshakeInterceptor.TOKEN_ATTR);
    }

    @Test
    void connect_autenticaPeloTokenDoHandshake() {
        User user = new User();
        user.setId(7L);
        when(jwtService.extractUserId("tok")).thenReturn(7L);
        when(jwtService.isTokenValid(eq("tok"), any(User.class))).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Map<String, Object> attributes = handshakeWith(new Cookie(SessionCookie.NAME, "tok"));

        Message<?> out = stomp.preSend(connect(attributes), null);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(attributes).containsEntry("myrank_uid", 7L);
    }

    @Test
    void connect_semCookieNemHeader_recusa() {
        Map<String, Object> attributes = handshakeWith();

        assertThatThrownBy(() -> stomp.preSend(connect(attributes), null))
                .isInstanceOf(MessagingException.class);
    }
}
