package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Bloco "main_picture" da API oficial do MyAnimeList: { medium, large }. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MalPictureDTO {

    private String medium;
    private String large;

    public String getMedium() { return medium; }
    public void setMedium(String medium) { this.medium = medium; }

    public String getLarge() { return large; }
    public void setLarge(String large) { this.large = large; }

    /** Prefere a variante grande; cai pra média; null se nenhuma existir. */
    public String resolveBest() {
        if (large != null && !large.isBlank()) return large;
        if (medium != null && !medium.isBlank()) return medium;
        return null;
    }
}
