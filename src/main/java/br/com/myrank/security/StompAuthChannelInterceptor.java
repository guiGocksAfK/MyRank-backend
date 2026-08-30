package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.ConversationMemberRepository;
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
import java.util.regex.Pattern;

/**
 * Autentica o frame CONNECT do STOMP pelo header Authorization (mesmo JWT do REST)
 * e barra SUBSCRIBE em /topic/conversation.{id} de quem não é membro da conversa.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CONV_TOPIC = Pattern.compile("^/topic/conversation\\.(\\d+)$");
    private static final String UID_ATTR = "myrank_uid";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final ConversationMemberRepository memberRepository;

    public StompAuthChannelInterceptor(JwtService jwtService,
                                       CustomUserDetailsService userDetailsService,
                                       UserRepository userRepository,
                                       ConversationMemberRepository memberRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            throw new MessagingException("Sessão de chat sem token.");
        }
        String token = bearer.substring(7);
        String email;
        try {
            email = jwtService.extractUsername(token);
        } catch (Exception e) {
            throw new MessagingException("Token de chat inválido.");
        }
        if (email == null || !jwtService.isTokenValid(token, email)) {
            throw new MessagingException("Token de chat inválido.");
        }
        UserDetails ud = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        accessor.setUser(auth);

        Long uid = userRepository.findByEmail(email).map(User::getId).orElse(null);
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && uid != null) attrs.put(UID_ATTR, uid);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null) return;
        var m = CONV_TOPIC.matcher(dest);
        if (!m.matches()) return; // /user/queue/** é privado por destino, não precisa de checagem

        Long convId = Long.valueOf(m.group(1));
        Long uid = currentUid(accessor);
        if (uid == null || !memberRepository.existsByConversationIdAndUserId(convId, uid)) {
            throw new MessagingException("Você não participa dessa conversa.");
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
