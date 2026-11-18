package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.UserResponseDTO;
import br.com.myrank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Conectar Discord" das Configurações: é o vínculo que o bot de Discord usa. */
class UserDiscordConnectionTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users, mock(PasswordEncoder.class));

    private User user(long id, String discordId) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        u.setDiscordId(discordId);
        when(users.findById(id)).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        return u;
    }

    @Test
    void conecta_qualquerContaLogada_semOlharEmailNemComoEntra() {
        user(7L, null);
        when(users.findByDiscordId("123")).thenReturn(Optional.empty());

        User saved = service.connectDiscord(7L, "123");

        assertThat(saved.getDiscordId()).isEqualTo("123");
        assertThat(UserResponseDTO.fromEntity(saved).discordConnected()).isTrue();
    }

    @Test
    void conectarDeNovoOMesmoDiscord_naoDaErro() {
        User me = user(7L, "123");
        when(users.findByDiscordId("123")).thenReturn(Optional.of(me));

        assertThat(service.connectDiscord(7L, "123").getDiscordId()).isEqualTo("123");
    }

    @Test
    void discordDeOutraConta_recusa_eNaoMexeEmNada() {
        user(7L, null);
        User outra = user(8L, "123");
        when(users.findByDiscordId("123")).thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> service.connectDiscord(7L, "123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outra conta");
        verify(users, never()).save(any(User.class));
    }

    @Test
    void desconecta() {
        user(7L, "123");

        User saved = service.disconnectDiscord(7L);

        assertThat(saved.getDiscordId()).isNull();
        assertThat(UserResponseDTO.fromEntity(saved).discordConnected()).isFalse();
    }
}
