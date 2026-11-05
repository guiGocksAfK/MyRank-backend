package br.com.myrank.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** O que um usuário editou num criador (foto própria e/ou tipo). Vale só pra ele. */
@Entity
@Table(name = "user_creators")
public class UserCreator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "creator_key", nullable = false, length = 200)
    private String creatorKey;

    @Column(name = "photo_url", length = 1000)
    private String photoUrl;

    @Column(name = "creator_type", length = 20)
    private String creatorType;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserCreator() {}

    public UserCreator(Long userId, String creatorKey) {
        this.userId = userId;
        this.creatorKey = creatorKey;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCreatorKey() { return creatorKey; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getCreatorType() { return creatorType; }
    public void setCreatorType(String creatorType) { this.creatorType = creatorType; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
