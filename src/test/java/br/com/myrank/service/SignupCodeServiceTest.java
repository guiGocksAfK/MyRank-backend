package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignupCodeServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BrevoEmailClient emailClient = mock(BrevoEmailClient.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));
    private final SignupCodeService service = new SignupCodeService(userRepository, emailClient, SECRET, clock);

    @BeforeEach
    void setUp() {
        when(emailClient.isConfigured()).thenReturn(true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    /** Manda o código e devolve o que foi pro email (a memória só guarda o hash). */
    private String sendAndCapture(String email) {
        service.sendCode(email, "PT");
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailClient).send(eq(email.trim()), anyString(), html.capture());
        clearInvocations(emailClient);
        Matcher m = Pattern.compile(">(\\d{6})</p>").matcher(html.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void codigoCerto_devolvePasseComOEmail_eSoValeUmaVez() {
        String code = sendAndCapture("novo@myrank.dev");

        String pass = service.verifyCode("NOVO@myrank.dev", code);

        assertThat(service.emailFromPass(pass)).isEqualTo("NOVO@myrank.dev");
        assertThatThrownBy(() -> service.verifyCode("novo@myrank.dev", code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cincoErros_derrubamOCodigo() {
        String code = sendAndCapture("novo@myrank.dev");
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verifyCode("novo@myrank.dev", wrong))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.verifyCode("novo@myrank.dev", code))
                .hasMessage("Código expirado. Peça um novo.");
    }

    @Test
    void codigoVencido_falha() {
        String code = sendAndCapture("novo@myrank.dev");
        clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> service.verifyCode("novo@myrank.dev", code))
                .hasMessage("Código expirado. Peça um novo.");
    }

    @Test
    void reenvioImediato_eBarrado_depoisDoIntervaloDerrubaOAnterior() {
        String first = sendAndCapture("novo@myrank.dev");
        assertThatThrownBy(() -> service.sendCode("novo@myrank.dev", "PT"))
                .isInstanceOf(IllegalArgumentException.class);

        clock.advance(Duration.ofSeconds(31));
        String second = sendAndCapture("novo@myrank.dev");

        if (!first.equals(second)) {
            assertThatThrownBy(() -> service.verifyCode("novo@myrank.dev", first))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(service.verifyCode("novo@myrank.dev", second)).isNotBlank();
    }

    @Test
    void emailDeContaConfirmada_eRecusado_semMandarEmail() {
        User owner = new User();
        owner.setAuthProvider(AuthProvider.LOCAL);
        owner.setEmailVerified(true);
        when(userRepository.findByEmail("dono@myrank.dev")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.sendCode("dono@myrank.dev", "PT"))
                .hasMessage("Email já está em uso.");
        verify(emailClient, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void cadastroPendenteDoFluxoAntigo_naoPrendeOEmail() {
        User pending = new User();
        pending.setAuthProvider(AuthProvider.LOCAL);
        pending.setEmailVerified(false);
        when(userRepository.findByEmail("dono@myrank.dev")).thenReturn(Optional.of(pending));

        assertThat(sendAndCapture("dono@myrank.dev")).hasSize(6);
    }

    @Test
    void passeVencido_ouDeOutraChave_eRecusado() {
        String pass = service.verifyCode("novo@myrank.dev", sendAndCapture("novo@myrank.dev"));
        SignupCodeService otherKey = new SignupCodeService(userRepository, emailClient, SECRET + "x", clock);

        assertThatThrownBy(() -> otherKey.emailFromPass(pass)).isInstanceOf(IllegalArgumentException.class);
        clock.advance(Duration.ofMinutes(31));
        assertThatThrownBy(() -> service.emailFromPass(pass)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) { this.now = start; }

        void advance(Duration d) { now = now.plus(d); }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
