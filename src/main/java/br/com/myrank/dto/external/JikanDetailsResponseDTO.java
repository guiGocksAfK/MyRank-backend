package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JikanDetailsResponseDTO {

    private JikanAnimeDetailsDTO data;

    public JikanAnimeDetailsDTO getData() { return data; }
    public void setData(JikanAnimeDetailsDTO data) { this.data = data; }
}