package br.com.myrank.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "works")
public class Work {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 255)
    private String creator;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "time_minutes", nullable = false)
    private int timeMinutes = 0;

    @Column
    private Integer position;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal score;

    @Column(name = "time_bonus_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal timeBonusScore = BigDecimal.ZERO;

    @Column(name = "final_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Work() {}

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public int getTimeMinutes() { return timeMinutes; }
    public void setTimeMinutes(int timeMinutes) { this.timeMinutes = timeMinutes; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public BigDecimal getTimeBonusScore() { return timeBonusScore; }
    public void setTimeBonusScore(BigDecimal timeBonusScore) { this.timeBonusScore = timeBonusScore; }

    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}