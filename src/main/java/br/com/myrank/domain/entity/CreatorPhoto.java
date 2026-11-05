package br.com.myrank.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Foto automática de um criador (TMDB), compartilhada entre todos os usuários.
 * {@code photoUrl} nulo = procurou e não achou. Ver {@code CreatorProfileService}.
 */
@Entity
@Table(name = "creator_photos")
public class CreatorPhoto {

    /** PERSON (foto de perfil) ou COMPANY (logo do estúdio) + o nome normalizado. */
    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "kind", nullable = false, length = 10)
        private String kind;

        @Column(name = "creator_key", nullable = false, length = 200)
        private String creatorKey;

        public Id() {}

        public Id(String kind, String creatorKey) {
            this.kind = kind;
            this.creatorKey = creatorKey;
        }

        public String getKind() { return kind; }
        public String getCreatorKey() { return creatorKey; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id other && kind.equals(other.kind) && creatorKey.equals(other.creatorKey);
        }

        @Override
        public int hashCode() { return Objects.hash(kind, creatorKey); }
    }

    @EmbeddedId
    private Id id;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    public CreatorPhoto() {}

    public CreatorPhoto(Id id, String photoUrl, LocalDateTime fetchedAt) {
        this.id = id;
        this.photoUrl = photoUrl;
        this.fetchedAt = fetchedAt;
    }

    public Id getId() { return id; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
