package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleBooksSearchResponseDTO {

    private List<GoogleBookItemDTO> items;

    public List<GoogleBookItemDTO> getItems() { return items; }
    public void setItems(List<GoogleBookItemDTO> items) { this.items = items; }
}