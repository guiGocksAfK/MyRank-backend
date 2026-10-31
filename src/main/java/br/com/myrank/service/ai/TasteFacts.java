package br.com.myrank.service.ai;

import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.insight.InsightPayloadDTO.TasteSlice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * O que dá pra saber do gosto da pessoa sem IA: médias por tipo, gênero e criador,
 * o rigor das notas e as obras que destoam do próprio gênero. A IA recebe isso pronto
 * e só interpreta; o perfil de gosto da tela sai daqui, não do modelo.
 */
final class TasteFacts {

    /** Gênero ou criador só conta a partir de 2 obras; tensão, a partir de 3 no gênero. */
    private static final int MIN_GROUP = 2;
    private static final int MIN_TENSION_GROUP = 3;
    /** Distância da média do grupo pra uma nota virar "tensão" (candidata a contradição). */
    private static final double TENSION_GAP = 2.5;
    private static final int TAKE_MAX_CHARS = 160;
    private static final int GENRES_PER_WORK = 3;

    private record Stat(String name, double average, int count) {}

    private final List<Work> works;
    private final Map<Long, String> takes;

    /** {@code works} já vem ordenado por nota final, da maior pra menor. */
    TasteFacts(List<Work> works, Map<Long, String> takesByWorkId) {
        this.works = works;
        this.takes = takesByWorkId;
    }

    /** Uma linha compacta por obra: título | tipo | criador | ano | nota | horas | posição | gêneros | take. */
    String ranking() {
        Map<Long, Integer> positionInTable = new HashMap<>();
        Map<Long, Integer> seenPerTable = new HashMap<>();
        for (Work w : works) {
            Long tableId = w.getCategory() != null ? w.getCategory().getId() : null;
            positionInTable.put(w.getId(), seenPerTable.merge(tableId, 1, Integer::sum));
        }

        StringBuilder out = new StringBuilder();
        for (Work w : works) {
            List<String> parts = new ArrayList<>();
            parts.add("\"" + w.getTitle() + "\"");
            parts.add(typeName(w.getTemplate()));
            if (notBlank(w.getCreator())) parts.add(w.getCreator().trim());
            if (w.getReleaseDate() != null) parts.add(String.valueOf(w.getReleaseDate().getYear()));
            parts.add("nota " + score(w.getScore()));
            if (w.getTimeMinutes() > 0 && w.getTemplate().timeWeighted()) {
                parts.add(hours(w.getTimeMinutes()));
            }
            String table = w.getCategory() != null ? w.getCategory().getName() : null;
            if (table != null) parts.add("#" + positionInTable.get(w.getId()) + " em " + table);
            List<String> genres = genres(w);
            if (!genres.isEmpty()) parts.add(String.join(", ", genres.subList(0, Math.min(GENRES_PER_WORK, genres.size()))));
            String take = takes.get(w.getId());
            if (notBlank(take)) parts.add("take: \"" + shorten(take) + "\"");
            out.append(String.join(" | ", parts)).append('\n');
        }
        return out.toString();
    }

    /** Os números que a IA usa como base, em texto. */
    String summary() {
        StringBuilder out = new StringBuilder();

        double average = works.stream().mapToDouble(w -> value(w.getScore())).average().orElse(0);
        long tens = works.stream().filter(w -> value(w.getScore()) >= 10).count();
        out.append("Rigor: média geral ").append(decimal(average)).append(" em ").append(works.size())
                .append(" obras, ").append(tens).append(" com nota 10 — ").append(rigor(average)).append(".\n");

        out.append("Por tipo: ").append(join(byType())).append(".\n");

        List<Stat> genres = byGenre();
        out.append("Gêneros (2+ obras): ").append(genres.isEmpty() ? "sem dados suficientes" : join(genres)).append(".\n");

        List<Stat> creators = byCreator();
        out.append("Criadores (2+ obras): ").append(creators.isEmpty() ? "nenhum" : join(creators)).append(".\n");

        List<String> tensions = tensions();
        out.append("Tensões (notas bem abaixo da média do próprio gênero ou tipo): ")
                .append(tensions.isEmpty() ? "nenhuma" : String.join("; ", tensions)).append(".\n");
        return out.toString();
    }

    /**
     * Perfil de gosto da tela: os gêneros mais presentes, quando há pelo menos 3 com
     * 2+ obras; senão, os tipos (obras antigas, sem gêneros guardados).
     */
    List<TasteSlice> tasteProfile() {
        List<Stat> genres = byGenre();
        List<Stat> source = genres.size() >= 3 ? genres : byType();
        return source.stream()
                .limit(5)
                .map(s -> new TasteSlice(s.name(), null, Math.round(s.average() * 10) / 10.0, s.count()))
                .toList();
    }

    // ── agrupamentos ──────────────────────────────────────────────────

    private List<Stat> byType() {
        Map<String, List<Work>> groups = new LinkedHashMap<>();
        for (Work w : works) groups.computeIfAbsent(typePlural(w.getTemplate()), k -> new ArrayList<>()).add(w);
        return stats(groups, 1);
    }

    private List<Stat> byGenre() {
        Map<String, List<Work>> groups = new LinkedHashMap<>();
        for (Work w : works) {
            for (String g : genres(w)) groups.computeIfAbsent(g, k -> new ArrayList<>()).add(w);
        }
        return stats(groups, MIN_GROUP).stream().limit(6).toList();
    }

    private List<Stat> byCreator() {
        Map<String, List<Work>> groups = new LinkedHashMap<>();
        for (Work w : works) {
            if (notBlank(w.getCreator())) {
                groups.computeIfAbsent(w.getCreator().trim(), k -> new ArrayList<>()).add(w);
            }
        }
        return stats(groups, MIN_GROUP).stream()
                .sorted(Comparator.comparingDouble(Stat::average).reversed())
                .limit(5)
                .toList();
    }

    /**
     * Obras com nota bem abaixo da média do próprio gênero ou tipo, maiores distâncias
     * primeiro. Gêneros entram antes dos tipos, então num empate o gênero ganha.
     */
    private List<String> tensions() {
        Map<String, List<Work>> groups = new LinkedHashMap<>();
        for (Work w : works) {
            for (String g : genres(w)) groups.computeIfAbsent(g, k -> new ArrayList<>()).add(w);
        }
        for (Work w : works) {
            groups.computeIfAbsent(typePlural(w.getTemplate()), k -> new ArrayList<>()).add(w);
        }

        record Tension(String text, double gap, Long workId) {}
        List<Tension> found = new ArrayList<>();
        groups.forEach((name, list) -> {
            if (list.size() < MIN_TENSION_GROUP) return;
            double avg = list.stream().mapToDouble(w -> value(w.getScore())).average().orElse(0);
            for (Work w : list) {
                double gap = avg - value(w.getScore());
                if (gap >= TENSION_GAP) {
                    found.add(new Tension("\"" + w.getTitle() + "\" (" + score(w.getScore()) + ") em " + name
                            + ", onde a média é " + decimal(avg), gap, w.getId()));
                }
            }
        });

        found.sort(Comparator.comparingDouble(Tension::gap).reversed());
        Set<Long> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (Tension t : found) {
            if (out.size() < 4 && seen.add(t.workId())) out.add(t.text()); // uma vez por obra
        }
        return out;
    }

    /** Média e quantidade por grupo, só grupos com {@code min}+ obras; mais obras primeiro. */
    private static List<Stat> stats(Map<String, List<Work>> groups, int min) {
        return groups.entrySet().stream()
                .filter(e -> e.getValue().size() >= min)
                .map(e -> new Stat(e.getKey(),
                        e.getValue().stream().mapToDouble(w -> value(w.getScore())).average().orElse(0),
                        e.getValue().size()))
                .sorted(Comparator.comparingInt(Stat::count).reversed()
                        .thenComparing(Comparator.comparingDouble(Stat::average).reversed()))
                .toList();
    }

    // ── formatação ────────────────────────────────────────────────────

    /** Gêneros guardados no details pela busca (fase 4); obras antigas não têm. */
    private static List<String> genres(Work w) {
        Object raw = w.getDetails() == null ? null : w.getDetails().get("genres");
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().filter(Objects::nonNull).map(Object::toString)
                .filter(TasteFacts::notBlank).map(String::trim).distinct().toList();
    }

    private static String rigor(double average) {
        if (average >= 8.5) return "generoso (dá notas altas com facilidade)";
        if (average <= 6.5) return "exigente (nota alta é rara)";
        return "equilibrado";
    }

    private static String join(List<Stat> stats) {
        return String.join("; ", stats.stream()
                .map(s -> s.name() + " " + decimal(s.average()) + " (" + s.count() + ")")
                .toList());
    }

    private static String typeName(TableTemplate t) {
        return switch (t) {
            case MOVIE -> "filme";
            case TV -> "série";
            case ANIME -> "anime";
            case MANGA -> "mangá";
            case GAME -> "jogo";
            case BOOK -> "livro";
            case MUSIC -> "música";
            case ALBUM -> "álbum";
            case CUSTOM -> "outro";
        };
    }

    private static String typePlural(TableTemplate t) {
        return switch (t) {
            case MOVIE -> "Filmes";
            case TV -> "Séries";
            case ANIME -> "Animes";
            case MANGA -> "Mangás";
            case GAME -> "Jogos";
            case BOOK -> "Livros";
            case MUSIC -> "Músicas";
            case ALBUM -> "Álbuns";
            case CUSTOM -> "Outros";
        };
    }

    private static String hours(int minutes) {
        return minutes < 60 ? minutes + "min" : Math.round(minutes / 60.0) + "h";
    }

    private static String shorten(String take) {
        String flat = take.replaceAll("\\s+", " ").trim();
        return flat.length() <= TAKE_MAX_CHARS ? flat : flat.substring(0, TAKE_MAX_CHARS - 1) + "…";
    }

    private static double value(BigDecimal score) {
        return score == null ? 0 : score.doubleValue();
    }

    private static String score(BigDecimal score) {
        return score == null ? "0" : score.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
