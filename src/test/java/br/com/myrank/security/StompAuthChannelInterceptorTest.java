package br.com.myrank.security;

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

class StompAuthChannelInterceptorTest {

    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
            mock(JwtService.class), mock(CustomUserDetailsService.class),
            mock(UserRepository.class));

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
    void somenteCanaisPrivadosPodemSerAssinados() {
        for (String destination : new String[]{"/topic/conversation.42", "/topic/**",
                "/queue/**", "/user/outro/queue/chat", "/user/queue/*"}) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, 7L), null))
                    .isInstanceOf(MessagingException.class);
        }
        for (String destination : new String[]{"/user/queue/chat", "/user/queue/chat-events"}) {
            assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, 7L), null))
                    .doesNotThrowAnyException();
        }
    }
}
