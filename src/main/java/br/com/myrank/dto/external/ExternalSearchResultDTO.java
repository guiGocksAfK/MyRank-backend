package br.com.myrank.dto.external;

/**
 * Resultado leve de busca/autocomplete, devolvido ao frontend.
 * externalId é o id do provedor externo (TMDB, RAWG, MyAnimeList usam números;
 * Google Books usa strings alfanuméricas) — String cobre todos os casos.
 * O front reenvia esse id para o endpoint de detalhes quando o usuário
 * seleciona um item, sem precisar tratar formatos diferentes por provedor.
 */
public class ExternalSearchResultDTO {

    private String externalId;
    private String title;
    private String posterUrl;
    private String releaseDate; // formato ISO (yyyy-MM-dd), pode ser null
    /** Linha de apoio na lista de sugestões (ex.: artista da música). Pode ser null. */
    private String subtitle;

    public ExternalSearchResultDTO() {}

    public ExternalSearchResultDTO(String externalId, String title, String posterUrl, String releaseDate) {
        this.externalId = externalId;
        this.title = title;
        this.posterUrl = posterUrl;
        this.releaseDate = releaseDate;
    }

    public ExternalSearchResultDTO(String externalId, String title, String posterUrl, String releaseDate,
                                   String subtitle) {
        this(externalId, title, posterUrl, releaseDate);
        this.subtitle = subtitle;
    }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
}