package br.com.myrank.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_stats")
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "total_works_rated", nullable = false)
    private Integer totalWorksRated = 0;

    @Column(name = "total_hours_consumed", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalHoursConsumed = BigDecimal.ZERO;

    @Column(name = "average_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal averageScore = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UserStats() {}

    public UserStats(User user) {
        this.user = user;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getTotalWorksRated() { return totalWorksRated; }
    public void setTotalWorksRated(Integer totalWorksRated) { this.totalWorksRated = totalWorksRated; }

    public BigDecimal getTotalHoursConsumed() { return totalHoursConsumed; }
    public void setTotalHoursConsumed(BigDecimal totalHoursConsumed) { this.totalHoursConsumed = totalHoursConsumed; }

    public BigDecimal getAverageScore() { return averageScore; }
    public void setAverageScore(BigDecimal averageScore) { this.averageScore = averageScore; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}