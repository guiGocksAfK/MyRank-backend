package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbTvDetailsDTO {

    private Long id;
    private String name;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("first_air_date")
    private String firstAirDate;

    @JsonProperty("episode_run_time")
    private List<Integer> episodeRunTime; // séries: lista de durações típicas de episódio

    @JsonProperty("number_of_episodes")
    private Integer numberOfEpisodes;

    private List<TmdbGenreDTO> genres;

    private TmdbCreditsDTO credits;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public String getFirstAirDate() { return firstAirDate; }
    public void setFirstAirDate(String firstAirDate) { this.firstAirDate = firstAirDate; }

    public List<Integer> getEpisodeRunTime() { return episodeRunTime; }
    public void setEpisodeRunTime(List<Integer> episodeRunTime) { this.episodeRunTime = episodeRunTime; }

    public Integer getNumberOfEpisodes() { return numberOfEpisodes; }
    public void setNumberOfEpisodes(Integer numberOfEpisodes) { this.numberOfEpisodes = numberOfEpisodes; }

    public List<TmdbGenreDTO> getGenres() { return genres; }
    public void setGenres(List<TmdbGenreDTO> genres) { this.genres = genres; }

    public TmdbCreditsDTO getCredits() { return credits; }
    public void setCredits(TmdbCreditsDTO credits) { this.credits = credits; }

    /**
     * Duração total estimada: duração média de episódio x número de episódios.
     * Usado para alimentar timeMinutes no Work quando o usuário rankear a série inteira.
     */
    public int resolveTotalMinutes() {
        if (episodeRunTime == null || episodeRunTime.isEmpty() || numberOfEpisodes == null) {
            return 0;
        }
        int avgEpisodeLength = episodeRunTime.get(0);
        return avgEpisodeLength * numberOfEpisodes;
    }
}