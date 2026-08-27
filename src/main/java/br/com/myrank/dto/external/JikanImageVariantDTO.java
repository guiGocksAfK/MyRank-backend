package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JikanImageVariantDTO {

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("large_image_url")
    private String largeImageUrl;

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getLargeImageUrl() { return largeImageUrl; }
    public void setLargeImageUrl(String largeImageUrl) { this.largeImageUrl = largeImageUrl; }
}