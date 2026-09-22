package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BrevoEmailClient emailClient = mock(BrevoEmailClient.class);
    private final EmailVerificationService service =
            new EmailVerificationService(userRepository, emailClient, "https://myrank.dev/");

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailClient.isConfigured()).thenReturn(true);
    }

    private User pendingUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("<b>nome</b>");
        user.setEmail("novo@myrank.dev");
        user.setAuthProvider(AuthProvider.LOCAL);
        return user;
    }

    /** Emite o link e devolve o token que foi pro email (o banco só vê o hash). */
    private String issueAndCaptureToken(User user) {
        service.issueAndSend(user);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailClient).send(eq("novo@myrank.dev"), anyString(), html.capture());
        String marker = "https://myrank.dev/confirmar-email?token=";
        String rest = html.getValue().substring(html.getValue().indexOf(marker) + marker.length());
        return rest.substring(0, rest.indexOf('"'));
    }

    @Test
    void issue_salvaSoOHash_eEscapaOUsernameNoHtml() {
        User user = pendingUser();
        String token = issueAndCaptureToken(user);

        assertThat(user.getEmailVerificationTokenHash()).hasSize(64).isNotEqualTo(token);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailClient).send(anyString(), anyString(), html.capture());
        assertThat(html.getValue()).contains("&lt;b&gt;nome&lt;/b&gt;").doesNotContain("<b>nome</b>");
    }

    @Test
    void verify_comTokenDoEmail_confirma_eInvalidaOToken() {
        User user = pendingUser();
        String token = issueAndCaptureToken(user);
        when(userRepository.findByEmailVerificationTokenHash(user.getEmailVerificationTokenHash()))
                .thenReturn(Optional.of(user));

        service.verify(token);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationTokenHash()).isNull();
    }

    @Test
    void verify_expirado_falha() {
        User user = pendingUser();
        String token = issueAndCaptureToken(user);
        user.setEmailVerificationExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmailVerificationTokenHash(user.getEmailVerificationTokenHash()))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verify(token)).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void resend_ignoraContaJaConfirmada() {
        User user = pendingUser();
        user.setEmailVerified(true);
        when(userRepository.findByEmail("novo@myrank.dev")).thenReturn(Optional.of(user));

        service.resend("novo@myrank.dev");

        verify(emailClient, never()).send(anyString(), anyString(), anyString());
    }
}
