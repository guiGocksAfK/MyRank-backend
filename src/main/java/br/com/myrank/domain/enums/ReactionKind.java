package br.com.myrank.domain.enums;

/** Espelha o enum PG `reaction_kind`. O cliente manda "up" | "agree" | "disagree". */
public enum ReactionKind {
    UP, AGREE, DISAGREE;

    public static ReactionKind fromClient(String raw) {
        if (raw == null) throw new IllegalArgumentException("Reação não informada.");
        return switch (raw.trim().toLowerCase()) {
            case "up" -> UP;
            case "agree" -> AGREE;
            case "disagree" -> DISAGREE;
            default -> throw new IllegalArgumentException("Reação inválida: " + raw);
        };
    }

    public String toClient() {
        return name().toLowerCase();
    }
}
