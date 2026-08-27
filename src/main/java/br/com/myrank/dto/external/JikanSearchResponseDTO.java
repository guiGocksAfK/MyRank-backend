package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JikanSearchResponseDTO {

    private List<JikanAnimeItemDTO> data;

    public List<JikanAnimeItemDTO> getData() { return data; }
    public void setData(List<JikanAnimeItemDTO> data) { this.data = data; }
}