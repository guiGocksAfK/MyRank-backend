package br.com.myrank.service.social;

import br.com.myrank.domain.entity.Conversation;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.ConversationType;
import br.com.myrank.domain.enums.DmPolicy;
import br.com.myrank.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** "Quem pode te mandar mensagem": conversa direta nova respeita a escolha de quem recebe. */
class ChatDmPolicyTest {

    private static final long ME = 1L, OTHER = 2L;

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChatService service = new ChatService(conversations, mock(ConversationMemberRepository.class),
            mock(ConversationJoinRequestRepository.class), mock(ConversationBanRepository.class),
            mock(MessageRepository.class), mock(MessageReactionRepository.class), follows, users,
            mock(NotificationService.class));

    private void other(DmPolicy policy) {
        User other = new User();
        other.setId(OTHER);
        other.setDmPolicy(policy);
        when(users.findById(OTHER)).thenReturn(Optional.of(other));
        when(conversations.findDirectsBetween(ConversationType.DIRECT, ME, OTHER)).thenReturn(List.of());
    }

    @Test
    void soQuemElaSegue_eElaNaoMeSegue_naoAbreConversaNova() {
        other(DmPolicy.FOLLOWING);
        when(follows.existsByFollowerIdAndFollowedId(OTHER, ME)).thenReturn(false);

        assertThatThrownBy(() -> service.startDirect(ME, OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("só recebe mensagens");
        verify(conversations, never()).save(any(Conversation.class));
    }

    @Test
    void soQuemElaSegue_eElaMeSegue_abreNormalmente() {
        other(DmPolicy.FOLLOWING);
        when(follows.existsByFollowerIdAndFollowedId(OTHER, ME)).thenReturn(true);
        when(conversations.save(any(Conversation.class))).thenThrow(new IllegalStateException("chegou a criar"));

        // passou da regra: o próximo passo (criar a conversa) é o que estoura aqui
        assertThatThrownBy(() -> service.startDirect(ME, OTHER)).hasMessage("chegou a criar");
    }

    @Test
    void todoMundo_abreSemOlharQuemSegue() {
        other(DmPolicy.EVERYONE);
        when(conversations.save(any(Conversation.class))).thenThrow(new IllegalStateException("chegou a criar"));

        assertThatThrownBy(() -> service.startDirect(ME, OTHER)).hasMessage("chegou a criar");
        verify(follows, never()).existsByFollowerIdAndFollowedId(OTHER, ME);
    }
}
