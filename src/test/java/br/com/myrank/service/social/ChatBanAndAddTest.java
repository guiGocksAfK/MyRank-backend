package br.com.myrank.service.social;

import br.com.myrank.domain.entity.Conversation;
import br.com.myrank.domain.entity.ConversationBan;
import br.com.myrank.domain.entity.ConversationMember;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.ConversationAccess;
import br.com.myrank.domain.enums.ConversationMemberRole;
import br.com.myrank.domain.enums.ConversationType;
import br.com.myrank.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Expulsão bane, e só entra direto num grupo quem se segue mutuamente com quem adiciona. */
class ChatBanAndAddTest {

    private static final long GROUP = 7L, MOD = 1L, TARGET = 2L;

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationMemberRepository members = mock(ConversationMemberRepository.class);
    private final ConversationJoinRequestRepository requests = mock(ConversationJoinRequestRepository.class);
    private final ConversationBanRepository bans = mock(ConversationBanRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChatService service = new ChatService(conversations, members, requests, bans,
            mock(MessageRepository.class), mock(MessageReactionRepository.class), follows, users,
            mock(NotificationService.class));
    private final Conversation group = mock(Conversation.class);

    @BeforeEach
    void setUp() {
        when(group.getType()).thenReturn(ConversationType.GROUP);
        when(group.getId()).thenReturn(GROUP);
        when(group.getAccess()).thenReturn(ConversationAccess.OPEN);
        when(conversations.findById(GROUP)).thenReturn(Optional.of(group));
        when(conversations.findByInviteToken("convite")).thenReturn(Optional.of(group));
    }

    private ConversationMember member(long userId, ConversationMemberRole role) {
        ConversationMember m = mock(ConversationMember.class);
        when(m.getRole()).thenReturn(role);
        when(m.getUserId()).thenReturn(userId);
        when(members.findByConversationIdAndUserId(GROUP, userId)).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void expulsar_bane_eQuemFoiExpulsoNaoVoltaPeloDiretorioNemPeloLink() {
        member(MOD, ConversationMemberRole.MOD);
        member(TARGET, ConversationMemberRole.MEMBER);

        service.removeMember(MOD, GROUP, TARGET);

        ArgumentCaptor<ConversationBan> ban = ArgumentCaptor.forClass(ConversationBan.class);
        verify(bans).save(ban.capture());
        assertThat(ban.getValue().getUserId()).isEqualTo(TARGET);
        assertThat(ban.getValue().getBannedBy()).isEqualTo(MOD);

        when(bans.existsByConversationIdAndUserId(GROUP, TARGET)).thenReturn(true);
        assertThatThrownBy(() -> service.joinOrRequest(TARGET, GROUP))
                .hasMessage("Você foi removido desse grupo. Só a moderação pode te adicionar de novo.");
        assertThatThrownBy(() -> service.acceptInvite(TARGET, "convite"))
                .hasMessage("Você foi removido desse grupo. Só a moderação pode te adicionar de novo.");
    }

    @Test
    void sairPorContaPropria_naoBane() {
        member(TARGET, ConversationMemberRole.MEMBER);

        service.removeMember(TARGET, GROUP, TARGET);

        verify(bans, never()).save(any());
    }

    @Test
    void adicionarDesconhecido_eRecusado() {
        member(MOD, ConversationMemberRole.ADMIN);
        when(users.findAllById(any())).thenReturn(List.of(new User()));
        when(follows.findFollowedIds(MOD)).thenReturn(List.of(TARGET)); // eu sigo, mas ele não me segue

        assertThatThrownBy(() -> service.addMembers(MOD, GROUP, List.of(TARGET)))
                .hasMessage("Só dá pra adicionar quem vocês se seguem mutuamente. Os outros entram pelo link de convite.");
        verify(members, never()).save(any());
    }

    @Test
    void adicionarQuemSeSegueMutuamente_entraEDesfazAExpulsao() {
        member(MOD, ConversationMemberRole.ADMIN);
        when(users.findAllById(any())).thenReturn(List.of(new User()));
        when(follows.findFollowedIds(MOD)).thenReturn(List.of(TARGET));
        when(follows.findFollowerIds(MOD)).thenReturn(List.of(TARGET));

        service.addMembers(MOD, GROUP, List.of(TARGET));

        verify(bans).deleteByConversationIdAndUserId(GROUP, TARGET);
        verify(members).save(any(ConversationMember.class));
    }

    @Test
    void adicionarQuemPediuPraEntrar_valeMesmoSemSeguir() {
        member(MOD, ConversationMemberRole.ADMIN);
        when(users.findAllById(any())).thenReturn(List.of(new User()));
        when(requests.existsByConversationIdAndUserId(GROUP, TARGET)).thenReturn(true);

        service.addMembers(MOD, GROUP, List.of(TARGET));

        verify(members).save(any(ConversationMember.class));
    }

    @Test
    void criarGrupoComDesconhecido_eRecusado() {
        when(users.findAllById(any())).thenReturn(List.of(new User()));

        assertThatThrownBy(() -> service.createGroup(MOD, "Grupo", List.of(TARGET), "OPEN", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(conversations, never()).save(any());
    }
}
