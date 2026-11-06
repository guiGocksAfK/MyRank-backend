package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.DmPolicy;
import br.com.myrank.domain.enums.NotificationGroup;
import br.com.myrank.domain.enums.NotificationType;
import br.com.myrank.dto.UserResponseDTO;
import br.com.myrank.dto.UserUpdateDTO;
import br.com.myrank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Configurações da conta: mensagens, avisos silenciados, nota ponderada e sessões. */
class UserSettingsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users, mock(PasswordEncoder.class));

    private User user() {
        User u = new User();
        u.setId(7L);
        u.setUsername("guigocks");
        when(users.findById(7L)).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        return u;
    }

    private static UserUpdateDTO settings(String dmPolicy, List<String> muted, Boolean weighted) {
        return new UserUpdateDTO(null, null, null, null, null, dmPolicy, muted, weighted);
    }

    @Test
    void salvaMensagensAvisosENotaPonderada_eDevolveNoMe() {
        User u = user();

        service.updateUser(7L, settings("following", List.of("reactions", "TAKES"), true));

        assertThat(u.getDmPolicy()).isEqualTo(DmPolicy.FOLLOWING);
        assertThat(u.getMutedGroups()).containsExactlyInAnyOrder(NotificationGroup.REACTIONS, NotificationGroup.TAKES);
        assertThat(u.isWeightedByDefault()).isTrue();
        UserResponseDTO me = UserResponseDTO.fromEntity(u);
        assertThat(me.dmPolicy()).isEqualTo("FOLLOWING");
        assertThat(me.mutedNotifications()).containsExactly("REACTIONS", "TAKES");
        assertThat(me.authProvider()).isEqualTo("LOCAL");
    }

    @Test
    void camposNulos_naoMexemNoQueJaEstava() {
        User u = user();
        u.setDmPolicy(DmPolicy.FOLLOWING);
        u.setMutedGroups(EnumSet.of(NotificationGroup.GROUPS));

        service.updateUser(7L, settings(null, null, null));

        assertThat(u.getDmPolicy()).isEqualTo(DmPolicy.FOLLOWING);
        assertThat(u.getMutedGroups()).containsExactly(NotificationGroup.GROUPS);
    }

    @Test
    void listaVazia_voltaAReceberTudo() {
        User u = user();
        u.setMutedGroups(EnumSet.of(NotificationGroup.FOLLOWS));

        service.updateUser(7L, settings(null, List.of(), null));

        assertThat(u.getMutedGroups()).isEmpty();
    }

    @Test
    void valoresDesconhecidos_saoRecusados() {
        user();
        assertThatThrownBy(() -> service.updateUser(7L, settings("amigos", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateUser(7L, settings(null, List.of("SPAM"), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cadaTipoDeAviso_temUmGrupo_eSilenciarPegaSoOGrupo() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(NotificationGroup.of(type)).isNotNull();
        }
        User u = new User();
        u.setMutedGroups(EnumSet.of(NotificationGroup.FOLLOWS));
        assertThat(u.isMuted(NotificationType.FOLLOW)).isTrue();
        assertThat(u.isMuted(NotificationType.FOLLOW_REQUEST)).isTrue();
        assertThat(u.isMuted(NotificationType.REACTION)).isFalse();
    }

    @Test
    void sairDosOutrosAparelhos_sobeAVersaoDaSessao() {
        User u = user();
        u.setTokenVersion(3);

        service.revokeSessions(7L);

        assertThat(u.getTokenVersion()).isEqualTo(4);
        verify(users).save(u);
    }
}
