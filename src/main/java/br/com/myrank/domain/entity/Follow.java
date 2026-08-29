package br.com.myrank.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "follow")
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "followed_id", nullable = false)
    private Long followedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Follow() {}

    public Follow(Long followerId, Long followedId) {
        this.followerId = followerId;
        this.followedId = followedId;
    }

    public Long getId() { return id; }
    public Long getFollowerId() { return followerId; }
    public void setFollowerId(Long followerId) { this.followerId = followerId; }
    public Long getFollowedId() { return followedId; }
    public void setFollowedId(Long followedId) { this.followedId = followedId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
