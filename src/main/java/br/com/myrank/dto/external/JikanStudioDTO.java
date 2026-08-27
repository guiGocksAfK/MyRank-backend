package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JikanStudioDTO {

    @JsonProperty("mal_id")
    private Long malId;

    private String name;

    public Long getMalId() { return malId; }
    public void setMalId(Long malId) { this.malId = malId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}