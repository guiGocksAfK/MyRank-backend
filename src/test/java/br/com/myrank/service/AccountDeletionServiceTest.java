package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.AccountDeleteRequestDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.social.ChatService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ChatService chatService = mock(ChatService.class);
    private final AccountDeletionService service =
            new AccountDeletionService(userRepository, passwordEncoder, chatService);

    private User user(String passwordHash) {
        User user = new User();
        user.setId(42L);
        user.setUsername("dono");
        user.setPasswordHash(passwordHash);
        return user;
    }

    @Test
    void usernameErrado_naoApagaNada() {
        assertThatThrownBy(() -> service.deleteAccount(user(null), new AccountDeleteRequestDTO(null, "outro")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).hardDeleteById(anyLong());
        verify(chatService, never()).releaseForAccountDeletion(anyLong());
    }

    @Test
    void contaComSenha_exigeSenhaCerta() {
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteAccount(user("hash"), new AccountDeleteRequestDTO("errada", "dono")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Senha incorreta.");
        assertThatThrownBy(() -> service.deleteAccount(user("hash"), new AccountDeleteRequestDTO(null, "dono")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).hardDeleteById(anyLong());
    }

    @Test
    void contaSoSocial_apagaSoComOUsername_eLiberaOChatAntes() {
        service.deleteAccount(user(null), new AccountDeleteRequestDTO(null, " dono "));

        // O chat tem que ser liberado antes: o DELETE em cascata apagaria os grupos criados por ele.
        InOrder order = inOrder(chatService, userRepository);
        order.verify(chatService).releaseForAccountDeletion(42L);
        order.verify(userRepository).hardDeleteById(42L);
    }
}
