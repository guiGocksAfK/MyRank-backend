package br.com.myrank.dto.external;

import java.util.Map;

/**
 * Detalhes completos de uma obra externa, já no formato que o front
 * usa para pré-preencher o formulário de criação de Work.
 * O usuário ainda confirma/edita antes de efetivamente salvar (POST /api/works).
 */
public class ExternalWorkDetailsDTO {

    private String title;
    private String imageUrl;
    private String creator;       // diretor (filme) ou criador/showrunner (série)
    private String releaseDate;   // formato ISO (yyyy-MM-dd)
    private int timeMinutes;      // duração do filme, ou duração total estimada da série
    /** Campos próprios do template (ex.: álbum da faixa); o front guarda em works.details. */
    private Map<String, Object> details;

    public ExternalWorkDetailsDTO() {}

    public ExternalWorkDetailsDTO(String title, String imageUrl, String creator,
                                  String releaseDate, int timeMinutes) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.creator = creator;
        this.releaseDate = releaseDate;
        this.timeMinutes = timeMinutes;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public int getTimeMinutes() { return timeMinutes; }
    public void setTimeMinutes(int timeMinutes) { this.timeMinutes = timeMinutes; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}