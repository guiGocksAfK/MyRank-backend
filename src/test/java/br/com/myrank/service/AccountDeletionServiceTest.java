package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.AccountDeleteRequestDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.service.social.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChatService chatService = mock(ChatService.class);
    private final BrevoEmailClient emailClient = mock(BrevoEmailClient.class);
    private final AccountDeletionService service =
            new AccountDeletionService(userRepository, chatService, emailClient);

    @BeforeEach
    void setUp() {
        when(emailClient.isConfigured()).thenReturn(true);
    }

    private User user(String passwordHash) {
        User user = new User();
        user.setId(42L);
        user.setUsername("dono");
        user.setEmail("dono@example.com");
        user.setPasswordHash(passwordHash);
        return user;
    }

    /** Pede o código e devolve o que foi pro email (o banco só guarda o hash). */
    private String issueAndCaptureCode(User user) {
        service.issueDeletionCode(user);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailClient).send(eq("dono@example.com"), anyString(), html.capture());
        Matcher matcher = Pattern.compile(">([A-Z2-9]{8})<").matcher(html.getValue());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void usernameErrado_naoApagaNada() {
        assertThatThrownBy(() -> service.deleteAccount(user(null), new AccountDeleteRequestDTO("outro", "X")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).hardDeleteById(anyLong());
        verify(chatService, never()).releaseForAccountDeletion(anyLong());
    }

    @Test
    void contaComSenha_tambemExigeOCodigoDoEmail() {
        User comSenha = user("hash");
        assertThatThrownBy(() -> service.deleteAccount(comSenha, new AccountDeleteRequestDTO("dono", "ERRADO12")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).hardDeleteById(anyLong());

        String code = issueAndCaptureCode(comSenha);
        service.deleteAccount(comSenha, new AccountDeleteRequestDTO("dono", code));

        verify(userRepository).hardDeleteById(42L);
    }

    @Test
    void contaSoSocial_exigeCodigo_liberaOChatAntes_eMandaComprovante() {
        User social = user(null);
        String code = issueAndCaptureCode(social);
        assertThatThrownBy(() -> service.deleteAccount(social, new AccountDeleteRequestDTO("dono", "ERRADO12")))
                .isInstanceOf(IllegalArgumentException.class);

        service.deleteAccount(social, new AccountDeleteRequestDTO(" dono ", code.toLowerCase()));

        // O chat tem que ser liberado antes: o DELETE em cascata apagaria os grupos criados por ele.
        InOrder order = inOrder(chatService, userRepository);
        order.verify(chatService).releaseForAccountDeletion(42L);
        order.verify(userRepository).hardDeleteById(42L);
        // 1º email = código, 2º = comprovante de exclusão
        verify(emailClient, times(2)).send(eq("dono@example.com"), anyString(), anyString());
    }
}
