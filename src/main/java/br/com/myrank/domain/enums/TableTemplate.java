package br.com.myrank.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Identidade estável do conteúdo; nome e emoji da tabela são só apresentação. */
public enum TableTemplate {
    MOVIE("movie", "filme"),
    TV("tv", "serie"),
    GAME("game", "jogo"),
    BOOK("book", "livro"),
    ANIME("anime", "anime"),
    CUSTOM("custom", "outro");

    private final String value;
    private final String type;

    TableTemplate(String value, String type) {
        this.value = value;
        this.type = type;
    }

    @JsonValue
    public String value() { return value; }

    /** Mantém os identificadores usados pelo perfil, feed e conquistas. */
    public String type() { return type; }

    @JsonCreator
    public static TableTemplate fromValue(String value) {
        for (TableTemplate template : values()) {
            if (template.value.equals(value)) return template;
        }
        throw new IllegalArgumentException("Template inválido: " + value);
    }
}
