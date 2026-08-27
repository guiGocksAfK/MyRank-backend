package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JikanImagesDTO {

    private JikanImageVariantDTO jpg;

    public JikanImageVariantDTO getJpg() { return jpg; }
    public void setJpg(JikanImageVariantDTO jpg) { this.jpg = jpg; }
}