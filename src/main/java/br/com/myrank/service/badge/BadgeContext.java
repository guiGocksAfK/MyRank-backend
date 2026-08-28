package br.com.myrank.service.badge;

import java.util.List;

/**
 * Fotografia do estado do usuário usada pelas regras de badge.
 * Todos os números derivam de dados que o MyRank realmente coleta.
 */
public class BadgeContext {

    /** Uma obra achatada pro que as regras precisam. */
    public record WorkView(
            String type,          // jogo | filme | serie | livro | anime | outro
            double score,         // nota original 0..10
            int timeMinutes,
            String creatorKey,    // creator normalizado (lower/trim) ou null
            Integer releaseYear,  // ano de lançamento ou null
            java.time.LocalDate addedDate
    ) {
        double hours() { return timeMinutes / 60.0; }
    }

    private final List<WorkView> works;
    private final boolean hasAvatar;
    private final boolean hasBio;
    private final boolean hasCustomCategory;
    private final boolean hasMasterGroup;
    private final long accountAgeDays;

    public BadgeContext(List<WorkView> works, boolean hasAvatar, boolean hasBio,
                        boolean hasCustomCategory, boolean hasMasterGroup, long accountAgeDays) {
        this.works = works;
        this.hasAvatar = hasAvatar;
        this.hasBio = hasBio;
        this.hasCustomCategory = hasCustomCategory;
        this.hasMasterGroup = hasMasterGroup;
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
                .filter(w -> w.type().equals(type) && w.creatorKey() != null)
                .map(BadgeContext.WorkView::creatorKey)
                .distinct().count();
    }

    public int distinctAddDays() {
        return (int) works.stream()
                .map(BadgeContext.WorkView::addedDate)
                .filter(java.util.Objects::nonNull)
                .distinct().count();
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

    // ── notas ─────────────────────────────────────────────────
    public boolean hasTenOfType(String type) {
        return works.stream().anyMatch(w -> w.type().equals(type) && w.score() >= TEN);
    }

    public long tensTotal() {
        return works.stream().filter(w -> w.score() >= TEN).count();
    }

    public int countScoreGte(double v) {
        return (int) works.stream().filter(w -> w.score() >= v).count();
    }

    public boolean hasScoreLte(double v) {
        return works.stream().anyMatch(w -> w.score() <= v);
    }

    public double avgOfType(String type) {
        return works.stream().filter(w -> w.type().equals(type))
                .mapToDouble(BadgeContext.WorkView::score).average().orElse(0);
    }

    public double avgAll() {
        return works.stream().mapToDouble(BadgeContext.WorkView::score).average().orElse(0);
    }

    // ── lançamento ────────────────────────────────────────────
    public boolean hasWorkBefore(String type, int year) {
        return works.stream().anyMatch(w ->
                w.type().equals(type) && w.releaseYear() != null && w.releaseYear() < year);
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
    public boolean hasMasterGroup() { return hasMasterGroup; }
    public long accountAgeDays() { return accountAgeDays; }
}
