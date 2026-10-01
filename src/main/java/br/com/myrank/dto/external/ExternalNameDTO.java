package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Item { name } que várias APIs usam pra gêneros (RAWG, MyAnimeList). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalNameDTO {

    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
