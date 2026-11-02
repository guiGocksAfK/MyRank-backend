package br.com.myrank.service.code;

import br.com.myrank.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailCodeStoreTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T12:00:00Z"));
    private final EmailCodeStore store = new EmailCodeStore(clock, Duration.ofMinutes(15), Duration.ofSeconds(30), 5);

    @Test
    void cheioDeCodigosValidos_recusaEmailNovo_eVoltaAAceitarQuandoVencem() {
        for (int i = 0; i < 10_000; i++) {
            store.issue("pessoa" + i + "@myrank.dev");
        }

        assertThatThrownBy(() -> store.issue("mais-uma@myrank.dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Muitos pedidos de código agora. Tente de novo em alguns minutos.");

        clock.advance(Duration.ofMinutes(16));
        assertThat(store.issue("mais-uma@myrank.dev")).hasSize(6);
    }

    @Test
    void cheio_aindaDeixaQuemJaTemCodigoPedirOutro() {
        for (int i = 0; i < 10_000; i++) {
            store.issue("pessoa" + i + "@myrank.dev");
        }
        clock.advance(Duration.ofSeconds(31));

        assertThat(store.issue("pessoa0@myrank.dev")).hasSize(6);
    }
}
