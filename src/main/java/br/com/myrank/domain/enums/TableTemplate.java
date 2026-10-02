package br.com.myrank.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Identidade estável do conteúdo; nome e emoji da tabela são só apresentação. */
public enum TableTemplate {
    MOVIE("movie", "filme", true),
    TV("tv", "serie", true),
    GAME("game", "jogo", true),
    BOOK("book", "livro", true),
    ANIME("anime", "anime", true),
    // Música e álbum não usam ponderação por tempo: uma faixa de 8 min não foi
    // "mais consumida" que uma de 3.
    MUSIC("music", "musica", false),
    ALBUM("album", "album", false),
    // Mangá também não: tempo de leitura não é informado e não faz sentido estimar.
    MANGA("manga", "manga", false),
    CUSTOM("custom", "outro", true);

    private final String value;
    private final String type;
    private final boolean timeWeighted;

    TableTemplate(String value, String type, boolean timeWeighted) {
        this.value = value;
        this.type = type;
        this.timeWeighted = timeWeighted;
    }

    @JsonValue
    public String value() { return value; }

    /** Mantém os identificadores usados pelo perfil, feed e conquistas. */
    public String type() { return type; }

    /** Se o tempo dedicado vira bônus na nota final. */
    public boolean timeWeighted() { return timeWeighted; }

    @JsonCreator
    public static TableTemplate fromValue(String value) {
        for (TableTemplate template : values()) {
            if (template.value.equals(value)) return template;
        }
        throw new IllegalArgumentException("Template inválido: " + value);
    }
}
