package br.com.myrank.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Foto de perfil enviada pelo usuário, guardada como bytes no banco.
 * Tabela separada (PK = user_id) pra não trazer o BYTEA junto do User
 * em toda request autenticada. A FK/cascade fica só no banco (V1).
 */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private byte[] image;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public UserAvatar() {}

    public UserAvatar(Long userId, byte[] image, String contentType) {
        this.userId = userId;
        this.image = image;
        this.contentType = contentType;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public byte[] getImage() { return image; }
    public void setImage(byte[] image) { this.image = image; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
