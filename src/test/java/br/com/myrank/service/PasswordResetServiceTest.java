package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.dto.auth.ForgotPasswordResponseDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BrevoEmailClient emailClient = mock(BrevoEmailClient.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));
    private final PasswordResetService service =
            new PasswordResetService(userRepository, emailClient, encoder, SECRET, clock);

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(emailClient.isConfigured()).thenReturn(true);
    }

    private User localUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("<b>nome</b>");
        user.setEmail("conta@myrank.dev");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash(encoder.encode("senha-antiga"));
        when(userRepository.findByEmail("conta@myrank.dev")).thenReturn(Optional.of(user));
        return user;
    }

    /** Pede a redefinição e devolve o código que foi pro email. */
    private String requestAndCaptureCode() {
        service.request("conta@myrank.dev");
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailClient).send(eq("conta@myrank.dev"), anyString(), html.capture());
        assertThat(html.getValue()).contains("&lt;b&gt;nome&lt;/b&gt;").doesNotContain("<b>nome</b>");
        Matcher m = Pattern.compile(">(\\d{6})</p>").matcher(html.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void codigoCerto_passeTrocaASenha_eConfirmaOEmail() {
        User user = localUser();
        user.setPasswordResetTokenHash("hash-do-link-antigo");
        String pass = service.verifyCode("conta@myrank.dev", requestAndCaptureCode());

        User saved = service.reset(pass, "senha-nova-123");

        assertThat(saved).isSameAs(user);
        assertThat(encoder.matches("senha-nova-123", user.getPasswordHash())).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getPasswordResetTokenHash()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(1); // sessões dos outros aparelhos caem
    }

    @Test
    void emailSemConta_respondeIgual_semMandarEmail_eOCodigoErraIgual() {
        ForgotPasswordResponseDTO response = service.request("ninguem@myrank.dev");

        assertThat(response).isEqualTo(ForgotPasswordResponseDTO.sent());
        verify(emailClient, never()).send(anyString(), anyString(), anyString());
        // mesma mensagem de uma conta real com código errado: não dá pra descobrir quem tem conta
        assertThatThrownBy(() -> service.verifyCode("ninguem@myrank.dev", "123456"))
                .hasMessage("Código incorreto.");
    }

    @Test
    void contaSoComGoogle_avisaOProvedor_eNaoMandaEmail() {
        User social = new User();
        social.setEmail("google@myrank.dev");
        social.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByEmail("google@myrank.dev")).thenReturn(Optional.of(social));

        ForgotPasswordResponseDTO response = service.request("google@myrank.dev");

        assertThat(response).isEqualTo(ForgotPasswordResponseDTO.social("GOOGLE"));
        verify(emailClient, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void codigoVencido_naoLiberaATroca() {
        User user = localUser();
        String code = requestAndCaptureCode();
        clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> service.verifyCode("conta@myrank.dev", code))
                .hasMessage("Código expirado. Peça um novo.");
        assertThat(encoder.matches("senha-antiga", user.getPasswordHash())).isTrue();
    }

    @Test
    void passeVencido_naoTrocaASenha() {
        User user = localUser();
        String pass = service.verifyCode("conta@myrank.dev", requestAndCaptureCode());
        clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> service.reset(pass, "senha-nova-123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(encoder.matches("senha-antiga", user.getPasswordHash())).isTrue();
    }

    @Test
    void passeDoCadastro_naoValeNaTrocaDeSenha() {
        localUser();
        SignupCodeService signup = new SignupCodeService(userRepository, emailClient, SECRET, clock);
        String signupPass;
        // o cadastro recusa email de conta confirmada; aqui só interessa o passe
        when(userRepository.findByEmail("outro@myrank.dev")).thenReturn(Optional.empty());
        signup.sendCode("outro@myrank.dev", "PT");
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailClient).send(eq("outro@myrank.dev"), anyString(), html.capture());
        Matcher m = Pattern.compile(">(\\d{6})</p>").matcher(html.getValue());
        assertThat(m.find()).isTrue();
        signupPass = signup.verifyCode("outro@myrank.dev", m.group(1));

        assertThatThrownBy(() -> service.reset(signupPass, "senha-nova-123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
