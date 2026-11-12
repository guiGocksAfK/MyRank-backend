package br.com.myrank.config;

import br.com.myrank.security.SessionCookieHandshakeInterceptor;
import br.com.myrank.security.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-time do chat via STOMP sobre WebSocket (SockJS fallback).
 * Broker em memória (SimpleBroker) — serve o volume atual num único nó;
 * escalar horizontal exige um broker externo (RabbitMQ/Redis).
 * Envio de mensagem continua REST; o WS só empurra o recebimento.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final SessionCookieHandshakeInterceptor sessionCookieHandshakeInterceptor;

    /** Mesma lista de origens do CORS REST — ver {@code cors.allowed-origins}. */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor,
                           SessionCookieHandshakeInterceptor sessionCookieHandshakeInterceptor) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.sessionCookieHandshakeInterceptor = sessionCookieHandshakeInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // lê o cookie da sessão na abertura; vale também pros transportes do SockJS
                .addInterceptors(sessionCookieHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.split("\\s*,\\s*"))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
