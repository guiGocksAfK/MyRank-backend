package br.com.myrank.service.social;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.ConversationMemberRepository;
import br.com.myrank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatRealtimeServiceSecurityTest {
    @Test
    void eventsReachOnlyCurrentMembers() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        ConversationMemberRepository members = mock(ConversationMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        ChatRealtimeService service = new ChatRealtimeService(messaging, members, users);
        User member = new User();
        member.setId(2L);
        member.setEmail("member@example.com");
        when(members.findMemberIds(7L)).thenReturn(List.of(2L), List.of());
        when(users.findAllById(List.of(2L))).thenReturn(List.of(member));

        service.typing(7L, 2L, "Member");
        verify(messaging).convertAndSendToUser(eq("member@example.com"), eq("/queue/chat-events"), any());
        verifyNoMoreInteractions(messaging);

        clearInvocations(messaging);
        service.typing(7L, 2L, "Member");
        verifyNoInteractions(messaging);
    }
}
