package br.com.myrank.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Na abertura do WebSocket do chat, guarda o token do cookie da sessão nos
 * atributos da conexão. O front não enxerga mais o token (cookie HttpOnly), então
 * não tem como mandá-lo no CONNECT do STOMP: quem confere depois é o
 * {@link StompAuthChannelInterceptor}, a partir daqui. A origem do handshake já é
 * conferida pelo próprio endpoint (lista de origens liberadas).
 */
@Component
public class SessionCookieHandshakeInterceptor implements HandshakeInterceptor {

    /** Atributo da conexão com o token lido do cookie. */
    public static final String TOKEN_ATTR = "myrank_cookie_token";

    private final SessionCookie sessionCookie;

    public SessionCookieHandshakeInterceptor(SessionCookie sessionCookie) {
        this.sessionCookie = sessionCookie;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = sessionCookie.read(servletRequest.getServletRequest());
            if (token != null) attributes.put(TOKEN_ATTR, token);
        }
        // sem cookie a conexão abre mesmo assim: o CONNECT do STOMP é quem recusa
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nada a fazer
    }
}
