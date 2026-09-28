package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Autentica o frame CONNECT do STOMP pelo header Authorization (mesmo JWT do REST)
 * e só permite os canais privados do próprio usuário.
 * O cliente não publica eventos no broker; o servidor os envia após validar o REST.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String UID_ATTR = "myrank_uid";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public StompAuthChannelInterceptor(JwtService jwtService,
                                       CustomUserDetailsService userDetailsService,
                                       UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw new MessagingException("Publicação direta no chat não permitida.");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            throw new MessagingException("Sessão de chat sem token.");
        }
        String token = bearer.substring(7);
        Long uid;
        try {
            uid = jwtService.extractUserId(token);
        } catch (Exception e) {
            throw new MessagingException("Token de chat inválido.");
        }
        User user = uid == null ? null : userRepository.findById(uid).orElse(null);
        if (user == null || !jwtService.isTokenValid(token, user.getId())) {
            throw new MessagingException("Token de chat inválido.");
        }
        UserDetails ud = userDetailsService.loadUserByUsername(user.getEmail());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        accessor.setUser(auth);

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) attrs.put(UID_ATTR, uid);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (currentUid(accessor) == null
                || !("/user/queue/chat".equals(dest) || "/user/queue/chat-events".equals(dest))) {
            throw new MessagingException("Assinatura de chat não permitida.");
        }
    }

    private Long currentUid(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get(UID_ATTR) instanceof Long id) return id;
        // fallback: resolve pelo principal
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof UserDetails ud) {
            return userRepository.findByEmail(ud.getUsername()).map(User::getId).orElse(null);
        }
        return null;
    }
}
