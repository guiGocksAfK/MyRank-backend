package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JikanAnimeDetailsDTO {

    @JsonProperty("mal_id")
    private Long malId;

    private String title;

    private JikanImagesDTO images;

    private JikanAiredDTO aired;

    /** Número total de episódios. Pode ser null para animes em exibição (ainda não definido). */
    private Integer episodes;

    /** String livre tipo "24 min per ep" ou "1 hr 45 min" (filmes de anime). Precisa parsing manual. */
    private String duration;

    private List<JikanStudioDTO> studios;

    public Long getMalId() { return malId; }
    public void setMalId(Long malId) { this.malId = malId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public JikanImagesDTO getImages() { return images; }
    public void setImages(JikanImagesDTO images) { this.images = images; }

    public JikanAiredDTO getAired() { return aired; }
    public void setAired(JikanAiredDTO aired) { this.aired = aired; }

    public Integer getEpisodes() { return episodes; }
    public void setEpisodes(Integer episodes) { this.episodes = episodes; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public List<JikanStudioDTO> getStudios() { return studios; }
    public void setStudios(List<JikanStudioDTO> studios) { this.studios = studios; }

    /** Nomes dos estúdios, separados por vírgula. Null se a Jikan não tiver essa info. */
    public String resolveStudioNames() {
        if (studios == null || studios.isEmpty()) return null;
        return studios.stream()
                .map(JikanStudioDTO::getName)
                .filter(n -> n != null && !n.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    private static final Pattern HOURS_PATTERN = Pattern.compile("(\\d+)\\s*hr");
    private static final Pattern MINUTES_PATTERN = Pattern.compile("(\\d+)\\s*min");

    /**
     * Extrai a duração média de um episódio em minutos a partir da string livre
     * da Jikan (ex.: "24 min per ep" → 24; "1 hr 45 min" → 105).
     * Retorna 0 se não conseguir parsear.
     */
    private int parseEpisodeDurationMinutes() {
        if (duration == null || duration.isBlank()) return 0;

        int totalMinutes = 0;
        Matcher hourMatcher = HOURS_PATTERN.matcher(duration);
        if (hourMatcher.find()) {
            totalMinutes += Integer.parseInt(hourMatcher.group(1)) * 60;
        }
        Matcher minMatcher = MINUTES_PATTERN.matcher(duration);
        if (minMatcher.find()) {
            totalMinutes += Integer.parseInt(minMatcher.group(1));
        }
        return totalMinutes;
    }

    /**
     * Duração total estimada: duração média de episódio x número de episódios.
     * Para animes com episódios ainda não definidos (em exibição), usa 1 como fallback
     * para não zerar o tempo total (melhor que o usuário ajuste manualmente do que ver 0).
     */
    public int resolveTotalMinutes() {
        int perEpisode = parseEpisodeDurationMinutes();
        if (perEpisode <= 0) return 0;
        int episodeCount = (episodes != null && episodes > 0) ? episodes : 1;
        return perEpisode * episodeCount;
    }
}