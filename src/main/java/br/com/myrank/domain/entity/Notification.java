package br.com.myrank.domain.entity;

import br.com.myrank.domain.enums.NotificationType;
import br.com.myrank.domain.enums.ReactionKind;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Destinatário. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "notification_type")
    private NotificationType type;

    /** Último/único ator. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_count", nullable = false)
    private int actorCount = 1;

    @Column(name = "feed_event_id")
    private Long feedEventId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reaction_kind", columnDefinition = "reaction_kind")
    private ReactionKind reactionKind;

    @Column(nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Notification() {}

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public int getActorCount() { return actorCount; }
    public void setActorCount(int actorCount) { this.actorCount = actorCount; }

    public Long getFeedEventId() { return feedEventId; }
    public void setFeedEventId(Long feedEventId) { this.feedEventId = feedEventId; }

    public ReactionKind getReactionKind() { return reactionKind; }
    public void setReactionKind(ReactionKind reactionKind) { this.reactionKind = reactionKind; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
