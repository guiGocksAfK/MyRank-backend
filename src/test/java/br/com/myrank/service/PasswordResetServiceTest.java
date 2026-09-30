package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.dto.auth.ForgotPasswordResponseDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

class PasswordResetServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BrevoEmailClient emailClient = mock(BrevoEmailClient.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PasswordResetService service =
            new PasswordResetService(userRepository, emailClient, encoder, "https://myrank.dev/");

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailClient.isConfigured()).thenReturn(true);
    }

    private User localUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("<b>nome</b>");
        user.setEmail("conta@myrank.dev");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash(encoder.encode("senha-antiga"));
        return user;
    }

    /** Pede a redefinição e devolve o token que foi pro email (o banco só vê o hash). */
    private String requestAndCaptureToken(User user) {
        when(userRepository.findByEmail("conta@myrank.dev")).thenReturn(Optional.of(user));
        service.request("conta@myrank.dev");
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailClient).send(eq("conta@myrank.dev"), anyString(), html.capture());
        String marker = "https://myrank.dev/redefinir-senha?token=";
        String rest = html.getValue().substring(html.getValue().indexOf(marker) + marker.length());
        return rest.substring(0, rest.indexOf('"'));
    }

    @Test
    void request_contaComSenha_mandaLink_eSalvaSoOHash() {
        User user = localUser();
        String token = requestAndCaptureToken(user);

        assertThat(user.getPasswordResetTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(user.getPasswordResetExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(16));
    }

    @Test
    void request_emailSemConta_respondeIgualAContaComSenha_eNaoMandaEmail() {
        when(userRepository.findByEmail("ninguem@myrank.dev")).thenReturn(Optional.empty());

        ForgotPasswordResponseDTO response = service.request("ninguem@myrank.dev");

        assertThat(response).isEqualTo(ForgotPasswordResponseDTO.sent());
        verify(emailClient, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void request_contaSoComGoogle_avisaOProvedor_eNaoMandaEmail() {
        User social = new User();
        social.setEmail("google@myrank.dev");
        social.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByEmail("google@myrank.dev")).thenReturn(Optional.of(social));

        ForgotPasswordResponseDTO response = service.request("google@myrank.dev");

        assertThat(response).isEqualTo(ForgotPasswordResponseDTO.social("GOOGLE"));
        verify(emailClient, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void reset_comTokenDoEmail_trocaASenha_eInvalidaOToken() {
        User user = localUser();
        String token = requestAndCaptureToken(user);
        when(userRepository.findByPasswordResetTokenHash(user.getPasswordResetTokenHash()))
                .thenReturn(Optional.of(user));

        service.reset(token, "senha-nova-123");

        assertThat(encoder.matches("senha-nova-123", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordResetTokenHash()).isNull();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void reset_comTokenExpirado_recusa() {
        User user = localUser();
        String token = requestAndCaptureToken(user);
        user.setPasswordResetExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByPasswordResetTokenHash(user.getPasswordResetTokenHash()))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.reset(token, "senha-nova-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expirado");
        assertThat(encoder.matches("senha-antiga", user.getPasswordHash())).isTrue();
    }

    @Test
    void reset_comTokenDesconhecido_recusa() {
        when(userRepository.findByPasswordResetTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset("qualquer-coisa", "senha-nova-123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
