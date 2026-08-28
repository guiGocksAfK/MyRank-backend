package br.com.myrank.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Progresso de um usuário numa badge. {@code unlockedAt != null} = conquistada.
 * Uma vez conquistada, continua conquistada mesmo que o progresso caia depois
 * (ex.: o usuário apagou obras).
 */
@Entity
@Table(name = "user_badges")
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "badge_id", nullable = false)
    private Long badgeId;

    @Column(name = "current_progress", nullable = false)
    private int currentProgress = 0;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    public UserBadge() {}

    public UserBadge(Long userId, Long badgeId) {
        this.userId = userId;
        this.badgeId = badgeId;
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getBadgeId() { return badgeId; }
    public void setBadgeId(Long badgeId) { this.badgeId = badgeId; }

    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) { this.currentProgress = currentProgress; }

    public LocalDateTime getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(LocalDateTime unlockedAt) { this.unlockedAt = unlockedAt; }
}
