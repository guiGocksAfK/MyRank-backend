package br.com.myrank.domain.entity;

import br.com.myrank.domain.enums.ReactionKind;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "feed_reactions")
public class FeedReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_event_id", nullable = false)
    private Long feedEventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "reaction_kind")
    private ReactionKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public FeedReaction() {}

    public FeedReaction(Long feedEventId, Long userId, ReactionKind kind) {
        this.feedEventId = feedEventId;
        this.userId = userId;
        this.kind = kind;
    }

    public Long getId() { return id; }
    public Long getFeedEventId() { return feedEventId; }
    public void setFeedEventId(Long feedEventId) { this.feedEventId = feedEventId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public ReactionKind getKind() { return kind; }
    public void setKind(ReactionKind kind) { this.kind = kind; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
