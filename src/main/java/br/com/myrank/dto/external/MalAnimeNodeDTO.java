package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * "node" de anime da API oficial do MyAnimeList (api.myanimelist.net/v2).
 * Serve tanto para a busca (só id/title/main_picture/start_date) quanto para os
 * detalhes (com num_episodes/average_episode_duration/studios pedidos via `fields`).
 * O endpoint GET /anime/{id} devolve esse objeto na raiz, sem wrapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MalAnimeNodeDTO {

    private Long id;

    private String title;

    @JsonProperty("main_picture")
    private MalPictureDTO mainPicture;

    /** Estreia. Pode vir completa (yyyy-MM-dd), parcial (yyyy-MM / yyyy) ou ausente. */
    @JsonProperty("start_date")
    private String startDate;

    /** Nº total de episódios. 0 quando ainda não definido (anime em exibição). */
    @JsonProperty("num_episodes")
    private Integer numEpisodes;

    /** Duração média de um episódio, EM SEGUNDOS. 0 quando o MAL não tem o dado. */
    @JsonProperty("average_episode_duration")
    private Integer averageEpisodeDuration;

    private List<MalStudioDTO> studios;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public MalPictureDTO getMainPicture() { return mainPicture; }
    public void setMainPicture(MalPictureDTO mainPicture) { this.mainPicture = mainPicture; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public Integer getNumEpisodes() { return numEpisodes; }
    public void setNumEpisodes(Integer numEpisodes) { this.numEpisodes = numEpisodes; }

    public Integer getAverageEpisodeDuration() { return averageEpisodeDuration; }
    public void setAverageEpisodeDuration(Integer averageEpisodeDuration) {
        this.averageEpisodeDuration = averageEpisodeDuration;
    }

    public List<MalStudioDTO> getStudios() { return studios; }
    public void setStudios(List<MalStudioDTO> studios) { this.studios = studios; }

    /** URL do pôster (grande → média), ou null. */
    public String resolveImageUrl() {
        return mainPicture != null ? mainPicture.resolveBest() : null;
    }

    /** Nomes dos estúdios separados por vírgula; null se o MAL não trouxer nenhum. */
    public String resolveStudioNames() {
        if (studios == null || studios.isEmpty()) return null;
        return studios.stream()
                .map(MalStudioDTO::getName)
                .filter(n -> n != null && !n.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    /**
     * Normaliza start_date para yyyy-MM-dd (formato usado pelas outras APIs).
     * Datas parciais são completadas com o 1º dia/mês; formato inesperado → null.
     */
    public String resolveReleaseDate() {
        if (startDate == null || startDate.isBlank()) return null;
        return switch (startDate.length()) {
            case 10 -> startDate;              // yyyy-MM-dd
            case 7  -> startDate + "-01";      // yyyy-MM
            case 4  -> startDate + "-01-01";   // yyyy
            default -> null;
        };
    }

    /**
     * Duração total estimada em minutos: (segundos por ep / 60) x nº de episódios.
     * Sem duração de episódio → 0 (usuário ajusta no formulário).
     * Episódios ainda não definidos → conta como 1, pra não zerar o total.
     */
    public int resolveTotalMinutes() {
        int perEpisodeMinutes = (averageEpisodeDuration != null && averageEpisodeDuration > 0)
                ? averageEpisodeDuration / 60
                : 0;
        if (perEpisodeMinutes <= 0) return 0;
        int episodeCount = (numEpisodes != null && numEpisodes > 0) ? numEpisodes : 1;
        return perEpisodeMinutes * episodeCount;
    }
}
