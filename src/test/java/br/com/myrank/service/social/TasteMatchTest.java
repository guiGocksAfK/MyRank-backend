package br.com.myrank.service.social;

import br.com.myrank.service.social.TasteMatch.Rated;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TasteMatchTest {

    private static List<Rated> list(String type, double... scores) {
        List<Rated> out = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) out.add(new Rated("Obra " + i, type, scores[i]));
        return out;
    }

    @Test
    void lessThanFiveInCommonHasNoPercentButKeepsTheCount() {
        var r = TasteMatch.compare(list("filme", 8, 9, 7, 6), list("filme", 8, 9, 7, 6));
        assertThat(r.shared()).isEqualTo(4);
        assertThat(r.pct()).isNull();
    }

    @Test
    void fewWorksArePulledTowardsTheMiddle() {
        // as mesmas notas: com 5 obras não chega perto de 100; com 40 chega
        double[] five = {9, 7, 8, 6, 10};
        int withFive = TasteMatch.compare(list("filme", five), list("filme", five)).pct();
        double[] many = new double[40];
        for (int i = 0; i < many.length; i++) many[i] = 5 + (i % 6);
        int withMany = TasteMatch.compare(list("filme", many), list("filme", many)).pct();
        assertThat(withFive).isBetween(70, 80);
        assertThat(withMany).isGreaterThan(90);
    }

    @Test
    void harshAndGenerousRatersWithTheSameOrderStillMatch() {
        // mesma ordem de preferência, uma pessoa 2 pontos mais exigente
        double[] generous = {10, 9, 8, 9, 10, 8, 9, 10, 9, 8};
        double[] harsh = new double[generous.length];
        for (int i = 0; i < harsh.length; i++) harsh[i] = generous[i] - 2;
        int sameOrder = TasteMatch.compare(list("filme", generous), list("filme", harsh)).pct();
        // mesmas médias, preferências invertidas
        double[] inverted = new double[generous.length];
        for (int i = 0; i < inverted.length; i++) inverted[i] = 18 - generous[i];
        int oppositeOrder = TasteMatch.compare(list("filme", generous), list("filme", inverted)).pct();
        assertThat(sameOrder).isGreaterThan(oppositeOrder);
    }

    @Test
    void sameTitleWithDifferentTypeIsNotTheSameWork() {
        var r = TasteMatch.compare(
                List.of(new Rated("Duna", "filme", 9)),
                List.of(new Rated("Duna", "livro", 4)));
        assertThat(r.shared()).isZero();
    }

    @Test
    void fightIsTheWorkWithTheBiggestGap() {
        var r = TasteMatch.compare(
                List.of(new Rated("A", "filme", 9), new Rated("B", "filme", 9.8), new Rated("C", "filme", 7)),
                List.of(new Rated("a ", "filme", 8.5), new Rated("B", "filme", 5), new Rated("C", "filme", 7)));
        assertThat(r.fight().title()).isEqualTo("B");
        assertThat(r.fight().mine()).isEqualTo(9.8);
        assertThat(r.fight().theirs()).isEqualTo(5);
    }
}
