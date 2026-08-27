package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleBookVolumeInfoDTO {

    /** Minutos estimados de leitura por página, usado como fallback quando o livro não tem duração real. */
    private static final int ESTIMATED_MINUTES_PER_PAGE = 2;

    private String title;

    private List<String> authors;

    @JsonProperty("publishedDate")
    private String publishedDate; // formato variável: "2005-11-15", "2005-11", ou só "2005"

    @JsonProperty("pageCount")
    private Integer pageCount;

    @JsonProperty("imageLinks")
    private GoogleBookImageLinksDTO imageLinks;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> authors) { this.authors = authors; }

    public String getPublishedDate() { return publishedDate; }
    public void setPublishedDate(String publishedDate) { this.publishedDate = publishedDate; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public GoogleBookImageLinksDTO getImageLinks() { return imageLinks; }
    public void setImageLinks(GoogleBookImageLinksDTO imageLinks) { this.imageLinks = imageLinks; }

    /** Nomes dos autores, separados por vírgula. Null se o Google Books não tiver essa info. */
    public String resolveAuthorNames() {
        if (authors == null || authors.isEmpty()) return null;
        return String.join(", ", authors);
    }

    /**
     * Normaliza publishedDate para yyyy-MM-dd (compatível com TMDB/RAWG/Jikan).
     * O Google Books às vezes só tem ano ("2005") ou ano-mês ("2005-11") —
     * nesses casos completa com 01 para manter o formato de data válido.
     */
    public String resolveDateOnly() {
        if (publishedDate == null || publishedDate.isBlank()) return null;
        int length = publishedDate.length();
        if (length == 10) return publishedDate;         // já é yyyy-MM-dd
        if (length == 7) return publishedDate + "-01";   // yyyy-MM → yyyy-MM-01
        if (length == 4) return publishedDate + "-01-01"; // yyyy → yyyy-01-01
        return publishedDate;
    }

    /** Estimativa de tempo de leitura em minutos, a partir do número de páginas. */
    public int resolveEstimatedMinutes() {
        if (pageCount == null || pageCount <= 0) return 0;
        return pageCount * ESTIMATED_MINUTES_PER_PAGE;
    }

    /** URL da capa em qualidade thumbnail (suficiente para os cards de sugestão e preview). */
    public String resolveImageUrl() {
        return imageLinks != null ? imageLinks.getThumbnail() : null;
    }
}