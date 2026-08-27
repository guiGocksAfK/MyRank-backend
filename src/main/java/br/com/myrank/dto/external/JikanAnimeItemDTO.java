package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JikanAnimeItemDTO {

    @JsonProperty("mal_id")
    private Long malId;

    private String title;

    private JikanImagesDTO images;

    /** Data de estreia; pode vir null para animes ainda não anunciados formalmente. */
    private JikanAiredDTO aired;

    public Long getMalId() { return malId; }
    public void setMalId(Long malId) { this.malId = malId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public JikanImagesDTO getImages() { return images; }
    public void setImages(JikanImagesDTO images) { this.images = images; }

    public JikanAiredDTO getAired() { return aired; }
    public void setAired(JikanAiredDTO aired) { this.aired = aired; }
}