package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JikanAiredDTO {

    /** Data de início da exibição, formato ISO completo (yyyy-MM-ddTHH:mm:ss+00:00). Pode ser null. */
    private String from;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    /** Retorna só a parte yyyy-MM-dd, compatível com o formato usado pelas outras APIs (TMDB/RAWG). */
    public String resolveDateOnly() {
        if (from == null || from.isBlank()) return null;
        return from.length() >= 10 ? from.substring(0, 10) : from;
    }
}