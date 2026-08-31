package br.com.myrank.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Comentário num take. Dois níveis: {@code parentCommentId} null = comentário
 * raiz; preenchido = resposta a um comentário raiz.
 */
@Entity
@Table(name = "take_comment")
public class TakeComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "take_id", nullable = false)
    private Long takeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** null = comentário raiz; senão = id do comentário raiz respondido. */
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** not null = comentário editado pelo autor. */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public TakeComment() {}

    public TakeComment(Long takeId, Long userId, Long parentCommentId, String text) {
        this.takeId = takeId;
        this.userId = userId;
        this.parentCommentId = parentCommentId;
        this.text = text;
    }

    public Long getId() { return id; }
    public Long getTakeId() { return takeId; }
    public void setTakeId(Long takeId) { this.takeId = takeId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Long parentCommentId) { this.parentCommentId = parentCommentId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(LocalDateTime editedAt) { this.editedAt = editedAt; }
}
