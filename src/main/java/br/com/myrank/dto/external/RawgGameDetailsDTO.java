package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RawgGameDetailsDTO {

    private Long id;
    private String name;

    @JsonProperty("background_image")
    private String backgroundImage;

    private String released;

    /** Média estimada de horas para concluir o jogo, segundo a comunidade RAWG. Pode ser 0 (sem dados). */
    private Integer playtime;

    private List<RawgDeveloperDTO> developers;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }

    public String getReleased() { return released; }
    public void setReleased(String released) { this.released = released; }

    public Integer getPlaytime() { return playtime; }
    public void setPlaytime(Integer playtime) { this.playtime = playtime; }

    public List<RawgDeveloperDTO> getDevelopers() { return developers; }
    public void setDevelopers(List<RawgDeveloperDTO> developers) { this.developers = developers; }

    /** Nomes das desenvolvedoras, separados por vírgula. Null se a RAWG não tiver essa info. */
    public String resolveDeveloperNames() {
        if (developers == null || developers.isEmpty()) {
            return null;
        }
        return developers.stream()
                .map(RawgDeveloperDTO::getName)
                .filter(n -> n != null && !n.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    /** Converte a estimativa de horas da RAWG (playtime) para minutos, usada pelo Work.timeMinutes. */
    public int resolveTimeMinutes() {
        return playtime != null ? playtime * 60 : 0;
    }
}