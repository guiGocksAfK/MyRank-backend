package br.com.myrank.service.badge;

import br.com.myrank.service.CreatorNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeContextTest {

    private static BadgeContext.WorkView serie(String creator) {
        return new BadgeContext.WorkView("serie", 9, 600, CreatorNames.keys(creator), 2010, null, false);
    }

    @Test
    void criadoresDistintos_contamCadaPessoaDoCampo_semRepetir() {
        BadgeContext context = new BadgeContext(List.of(
                serie("Vince Gilligan"),
                serie("Vince Gilligan, Peter Gould"),
                serie("vince gilligan"),
                serie(null)), false, false, false, false, 0);

        assertThat(context.distinctCreators("serie")).isEqualTo(2);
    }
}
