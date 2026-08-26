package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbSearchResponseDTO {

    private List<TmdbSearchItemDTO> results;

    public List<TmdbSearchItemDTO> getResults() { return results; }
    public void setResults(List<TmdbSearchItemDTO> results) { this.results = results; }
}