package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RawgSearchItemDTO {

    private Long id;
    private String name;

    @JsonProperty("background_image")
    private String backgroundImage;

    private String released; // formato yyyy-MM-dd, pode vir null (jogo TBA)

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }

    public String getReleased() { return released; }
    public void setReleased(String released) { this.released = released; }
}