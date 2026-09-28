package br.com.myrank.security;

import br.com.myrank.repository.ConversationMemberRepository;
import br.com.myrank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private final ConversationMemberRepository members = mock(ConversationMemberRepository.class);
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
            mock(JwtService.class), mock(CustomUserDetailsService.class),
            mock(UserRepository.class), members);

    private Message<byte[]> frame(StompCommand command, String destination, Long userId) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setSessionAttributes(Map.of("myrank_uid", userId));
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void clienteNaoPodePublicarDiretoEmTopicoNemFila() {
        for (String destination : new String[]{"/topic/conversation.42", "/queue/chat", "/app/other"}) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, destination, 7L), null))
                    .isInstanceOf(MessagingException.class);
        }
    }

    @Test
    void assinaturaDeConversaContinuaRestritaAosMembros() {
        Message<byte[]> subscription = frame(StompCommand.SUBSCRIBE, "/topic/conversation.42", 7L);
        assertThatThrownBy(() -> interceptor.preSend(subscription, null))
                .isInstanceOf(MessagingException.class);

        when(members.existsByConversationIdAndUserId(42L, 7L)).thenReturn(true);
        assertThatCode(() -> interceptor.preSend(subscription, null)).doesNotThrowAnyException();
    }
}
