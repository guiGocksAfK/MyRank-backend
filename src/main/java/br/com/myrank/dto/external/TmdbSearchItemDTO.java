package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mapeia um item bruto retornado por /search/movie ou /search/tv da TMDB.
 * Usado apenas internamente pelo TmdbService para deserializar a resposta;
 * o controller nunca expõe este DTO diretamente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbSearchItemDTO {

    private Long id;

    private String title;

    @JsonProperty("name")
    private String name; // séries usam "name" em vez de "title"

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("release_date")
    private String releaseDate;

    @JsonProperty("first_air_date")
    private String firstAirDate; // séries usam "first_air_date"

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public String getFirstAirDate() { return firstAirDate; }
    public void setFirstAirDate(String firstAirDate) { this.firstAirDate = firstAirDate; }

    /** Retorna o título correto, seja filme (title) ou série (name). */
    public String resolveTitle() {
        return title != null ? title : name;
    }

    /** Retorna a data correta, seja filme (release_date) ou série (first_air_date). */
    public String resolveDate() {
        return releaseDate != null ? releaseDate : firstAirDate;
    }
}