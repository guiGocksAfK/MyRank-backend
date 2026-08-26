package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbMovieDetailsDTO {

    private Long id;
    private String title;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("release_date")
    private String releaseDate;

    private Integer runtime; // duração do filme em minutos, já vem pronta

    private List<TmdbGenreDTO> genres;

    private TmdbCreditsDTO credits; // populado via append_to_response=credits

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public Integer getRuntime() { return runtime; }
    public void setRuntime(Integer runtime) { this.runtime = runtime; }

    public List<TmdbGenreDTO> getGenres() { return genres; }
    public void setGenres(List<TmdbGenreDTO> genres) { this.genres = genres; }

    public TmdbCreditsDTO getCredits() { return credits; }
    public void setCredits(TmdbCreditsDTO credits) { this.credits = credits; }
}