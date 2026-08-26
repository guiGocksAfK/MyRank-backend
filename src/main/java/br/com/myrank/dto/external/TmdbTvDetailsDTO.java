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
    private List<Integer> episodeRunTime; // pode vir vazio em séries mais novas/antigas

    @JsonProperty("number_of_episodes")
    private Integer numberOfEpisodes;

    @JsonProperty("last_episode_to_air")
    private TmdbEpisodeDTO lastEpisodeToAir; // fallback: runtime do último episódio exibido

    @JsonProperty("created_by")
    private List<TmdbCreatorDTO> createdBy; // criadores da série, campo próprio (não fica em credits)

    private List<TmdbGenreDTO> genres;

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

    public TmdbEpisodeDTO getLastEpisodeToAir() { return lastEpisodeToAir; }
    public void setLastEpisodeToAir(TmdbEpisodeDTO lastEpisodeToAir) { this.lastEpisodeToAir = lastEpisodeToAir; }

    public List<TmdbCreatorDTO> getCreatedBy() { return createdBy; }
    public void setCreatedBy(List<TmdbCreatorDTO> createdBy) { this.createdBy = createdBy; }

    public List<TmdbGenreDTO> getGenres() { return genres; }
    public void setGenres(List<TmdbGenreDTO> genres) { this.genres = genres; }

    /** Nomes dos criadores, separados por vírgula (ex.: "Vince Gilligan"). Null se a TMDB não tiver essa info. */
    public String resolveCreatorNames() {
        if (createdBy == null || createdBy.isEmpty()) {
            return null;
        }
        return createdBy.stream()
                .map(TmdbCreatorDTO::getName)
                .filter(n -> n != null && !n.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    /**
     * Duração total estimada: duração média de episódio x número de episódios.
     * episode_run_time é inconsistente na TMDB (às vezes vem vazio), então cai
     * para o runtime do último episódio exibido como fallback.
     */
    public int resolveTotalMinutes() {
        int avgEpisodeLength = resolveAvgEpisodeLength();
        if (avgEpisodeLength <= 0 || numberOfEpisodes == null) {
            return 0;
        }
        return avgEpisodeLength * numberOfEpisodes;
    }

    private int resolveAvgEpisodeLength() {
        if (episodeRunTime != null && !episodeRunTime.isEmpty()) {
            return episodeRunTime.get(0);
        }
        if (lastEpisodeToAir != null && lastEpisodeToAir.getRuntime() != null) {
            return lastEpisodeToAir.getRuntime();
        }
        return 0;
    }
}