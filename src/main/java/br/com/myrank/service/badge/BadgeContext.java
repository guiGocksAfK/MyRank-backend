package br.com.myrank.service.badge;

import java.util.List;

/**
 * Fotografia do estado do usuário usada pelas regras de badge.
 * Todos os números derivam de dados que o MyRank realmente coleta.
 */
public class BadgeContext {

    /** Uma obra achatada pro que as regras precisam. */
    public record WorkView(
            String type,          // jogo | filme | serie | livro | anime | manga | musica | album | outro
            double score,         // nota original 0..10
            int timeMinutes,
            List<String> creatorKeys, // cada pessoa do campo criador, normalizada (CreatorNames)
            Integer releaseYear,  // ano de lançamento ou null
            java.time.LocalDate addedDate,
            boolean edited,       // teve a nota/dados editados depois de cadastrada
            Long tableId          // tabela (categoria) onde a obra está
    ) {
        double hours() { return timeMinutes / 60.0; }
    }

    private final List<WorkView> works;
    private final boolean hasAvatar;
    private final boolean hasBio;
    private final boolean hasCustomCategory;
    private final long accountAgeDays;

    public BadgeContext(List<WorkView> works, boolean hasAvatar, boolean hasBio,
                        boolean hasCustomCategory, long accountAgeDays) {
        this.works = works;
        this.hasAvatar = hasAvatar;
        this.hasBio = hasBio;
        this.hasCustomCategory = hasCustomCategory;
        this.accountAgeDays = accountAgeDays;
    }

    private static final double TEN = 9.999; // "nota 10" com folga pra ponto flutuante
    private static final String[] CORE_TYPES = { "jogo", "filme", "serie", "livro", "anime" };

    // ── contagens ──────────────────────────────────────────────
    public int totalWorks() { return works.size(); }

    public int countType(String type) {
        return (int) works.stream().filter(w -> w.type().equals(type)).count();
    }

    public int distinctCreators(String type) {
        return (int) works.stream()
                .filter(w -> w.type().equals(type))
                .flatMap(w -> w.creatorKeys().stream())
                .distinct().count();
    }

    public int distinctAddDays() {
        return (int) works.stream()
                .map(BadgeContext.WorkView::addedDate)
                .filter(java.util.Objects::nonNull)
                .distinct().count();
    }

    /** Décadas de lançamento distintas (ex.: 1994 e 1998 contam como a mesma). */
    public int distinctDecades() {
        return (int) works.stream()
                .map(BadgeContext.WorkView::releaseYear)
                .filter(java.util.Objects::nonNull)
                .map(y -> y / 10)
                .distinct().count();
    }

    /** Décadas de lançamento distintas dentro de um tipo. */
    public int distinctDecadesOfType(String type) {
        return (int) works.stream()
                .filter(w -> w.type().equals(type) && w.releaseYear() != null)
                .map(w -> w.releaseYear() / 10)
                .distinct().count();
    }

    /** Quantas obras do tipo tem a pessoa (criador) que mais aparece nele. */
    public int maxWorksBySameCreator(String type) {
        return works.stream()
                .filter(w -> w.type().equals(type))
                .flatMap(w -> w.creatorKeys().stream().distinct())
                .collect(java.util.stream.Collectors.groupingBy(k -> k, java.util.stream.Collectors.counting()))
                .values().stream().mapToInt(Long::intValue).max().orElse(0);
    }

    /** Tabelas diferentes que têm ao menos uma obra do tipo. */
    public int distinctTablesOfType(String type) {
        return (int) works.stream()
                .filter(w -> w.type().equals(type) && w.tableId() != null)
                .map(BadgeContext.WorkView::tableId)
                .distinct().count();
    }

    public boolean hasEditedWork() {
        return works.stream().anyMatch(BadgeContext.WorkView::edited);
    }

    // ── horas ─────────────────────────────────────────────────
    public int hoursOfType(String type) {
        return (int) Math.round(works.stream()
                .filter(w -> w.type().equals(type))
                .mapToDouble(BadgeContext.WorkView::hours).sum());
    }

    public int totalHours() {
        return (int) Math.round(works.stream().mapToDouble(BadgeContext.WorkView::hours).sum());
    }

    public int maxSingleHoursOfType(String type) {
        return (int) Math.round(works.stream()
                .filter(w -> w.type().equals(type))
                .mapToDouble(BadgeContext.WorkView::hours).max().orElse(0));
    }

    /** Alguma obra do tipo com tempo registrado (> 0) de até `hours` horas. */
    public boolean hasShortWorkOfType(String type, int hours) {
        return works.stream().anyMatch(w ->
                w.type().equals(type) && w.timeMinutes() > 0 && w.timeMinutes() <= hours * 60);
    }

    // ── notas ─────────────────────────────────────────────────
    public long tensTotal() {
        return works.stream().filter(w -> w.score() >= TEN).count();
    }

    public int countScoreGte(double v) {
        return (int) works.stream().filter(w -> w.score() >= v).count();
    }

    public int countScoreLte(double v) {
        return (int) works.stream().filter(w -> w.score() <= v).count();
    }

    public int countScoreGteOfType(String type, double v) {
        return (int) works.stream().filter(w -> w.type().equals(type) && w.score() >= v).count();
    }

    public boolean hasScoreLte(double v) {
        return works.stream().anyMatch(w -> w.score() <= v);
    }

    public double avgAll() {
        return works.stream().mapToDouble(BadgeContext.WorkView::score).average().orElse(0);
    }

    // ── lançamento ────────────────────────────────────────────
    public boolean hasWorkBefore(String type, int year) {
        return works.stream().anyMatch(w ->
                w.type().equals(type) && w.releaseYear() != null && w.releaseYear() < year);
    }

    /** Avaliou no mesmo ano em que a obra saiu (pelo dia em que foi adicionada). */
    public boolean hasWorkInReleaseYear(String type) {
        return works.stream().anyMatch(w -> w.type().equals(type)
                && w.releaseYear() != null && w.addedDate() != null
                && w.releaseYear() == w.addedDate().getYear());
    }

    public boolean hasAnyWorkBefore(int year) {
        return works.stream().anyMatch(w -> w.releaseYear() != null && w.releaseYear() < year);
    }

    // ── cobertura de tipos ────────────────────────────────────
    public boolean allCoreTypesCovered() {
        for (String t : CORE_TYPES) if (countType(t) == 0) return false;
        return true;
    }

    // ── conta ─────────────────────────────────────────────────
    public boolean hasAvatar() { return hasAvatar; }
    public boolean hasBio() { return hasBio; }
    public boolean hasCustomCategory() { return hasCustomCategory; }
    public long accountAgeDays() { return accountAgeDays; }
}
