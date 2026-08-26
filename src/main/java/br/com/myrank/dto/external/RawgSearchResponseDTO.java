package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RawgSearchResponseDTO {

    private List<RawgSearchItemDTO> results;

    public List<RawgSearchItemDTO> getResults() { return results; }
    public void setResults(List<RawgSearchItemDTO> results) { this.results = results; }
}