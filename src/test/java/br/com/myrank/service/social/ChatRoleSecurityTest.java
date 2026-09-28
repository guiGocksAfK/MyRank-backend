package br.com.myrank.service.social;

import br.com.myrank.domain.entity.Conversation;
import br.com.myrank.domain.entity.ConversationMember;
import br.com.myrank.domain.enums.ConversationMemberRole;
import br.com.myrank.domain.enums.ConversationType;
import br.com.myrank.repository.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ChatRoleSecurityTest {
    @Test
    void adminCannotDemotePeerBeforeExpellingThem() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ConversationMemberRepository members = mock(ConversationMemberRepository.class);
        ChatService service = new ChatService(conversations, members,
                mock(ConversationJoinRequestRepository.class), mock(MessageRepository.class),
                mock(MessageReactionRepository.class), mock(FollowRepository.class),
                mock(UserRepository.class), mock(NotificationService.class));
        Conversation group = mock(Conversation.class);
        when(group.getType()).thenReturn(ConversationType.GROUP);
        when(conversations.findById(7L)).thenReturn(Optional.of(group));
        ConversationMember actingAdmin = mock(ConversationMember.class);
        ConversationMember targetAdmin = mock(ConversationMember.class);
        when(actingAdmin.getRole()).thenReturn(ConversationMemberRole.ADMIN);
        when(targetAdmin.getRole()).thenReturn(ConversationMemberRole.ADMIN);
        when(members.findByConversationIdAndUserId(7L, 1L)).thenReturn(Optional.of(actingAdmin));
        when(members.findByConversationIdAndUserId(7L, 2L)).thenReturn(Optional.of(targetAdmin));

        assertThatThrownBy(() -> service.setRole(1L, 7L, 2L, "MEMBER"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(members, never()).save(targetAdmin);
    }
}
