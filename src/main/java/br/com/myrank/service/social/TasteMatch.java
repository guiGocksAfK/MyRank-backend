package br.com.myrank.service.social;

import java.util.*;

/**
 * Gosto parecido entre duas pessoas. É a mesma conta do front
 * ({@code computeTasteMatch}, em socialData.js): as duas têm que dar o mesmo número.
 *
 * <ul>
 *   <li>Obra em comum = mesmo título (sem maiúscula nem espaço nas pontas) e mesmo
 *       tipo: "Duna" filme e "Duna" livro não são a mesma.</li>
 *   <li>Duas medidas, de 0 a 100, com 100 − 12 × diferença média:
 *       <b>nota</b> (as notas em si) e <b>gosto relativo</b> (o quanto cada um
 *       gostou acima ou abaixo da própria média). A relativa pesa mais: quem dá
 *       9 pra tudo não combina com todo mundo que também dá.</li>
 *   <li>Com poucas obras em comum a % é puxada pros 50% (como se houvesse
 *       {@link #SHRINK} obras "neutras" a mais). Com muitas, quase não muda.</li>
 *   <li>Só tem % com {@link #MIN_SHARED} obras em comum ou mais.</li>
 * </ul>
 */
public final class TasteMatch {

    /** Menos que isso em comum: a % ainda não diz nada. */
    public static final int MIN_SHARED = 5;
    /** "Pra debater" pede mais: discordar com poucas obras é acaso. */
    public static final int DEBATE_MIN_SHARED = 8;
    static final double SHRINK = 5;
    static final double RELATIVE_WEIGHT = 0.6;

    public record Rated(String title, String type, double score) {}

    /** A obra em que as duas notas ficam mais longe. */
    public record Fight(String title, double mine, double theirs) {}

    public record Result(int shared, Integer pct, Fight fight) {
        public static final Result NONE = new Result(0, null, null);
    }

    private TasteMatch() {}

    public static String key(String title, String type) {
        return (title == null ? "" : title.trim().toLowerCase(Locale.ROOT)) + "|" + (type == null ? "" : type);
    }

    public static Result compare(List<Rated> mine, List<Rated> theirs) {
        if (mine.isEmpty() || theirs.isEmpty()) return Result.NONE;
        double myAvg = mine.stream().mapToDouble(Rated::score).average().orElse(0);
        double theirAvg = theirs.stream().mapToDouble(Rated::score).average().orElse(0);

        Map<String, Double> myByKey = new HashMap<>();
        for (Rated r : mine) myByKey.putIfAbsent(key(r.title(), r.type()), r.score());

        Set<String> seen = new HashSet<>();
        double sumRaw = 0, sumRel = 0;
        int shared = 0;
        Fight fight = null;
        double fightGap = -1;
        for (Rated r : theirs) {
            String k = key(r.title(), r.type());
            Double my = myByKey.get(k);
            if (my == null || !seen.add(k)) continue;
            double raw = Math.abs(my - r.score());
            sumRaw += raw;
            sumRel += Math.abs((my - myAvg) - (r.score() - theirAvg));
            shared++;
            if (raw > fightGap) {
                fightGap = raw;
                fight = new Fight(r.title(), my, r.score());
            }
        }
        if (shared == 0) return Result.NONE;
        if (shared < MIN_SHARED) return new Result(shared, null, fight);

        double raw = 100 - 12 * (sumRaw / shared);
        double rel = 100 - 12 * (sumRel / shared);
        double combined = RELATIVE_WEIGHT * rel + (1 - RELATIVE_WEIGHT) * raw;
        double shrunk = (shared * combined + SHRINK * 50) / (shared + SHRINK);
        int pct = (int) Math.round(Math.max(0, Math.min(100, shrunk)));
        return new Result(shared, pct, fight);
    }
}
