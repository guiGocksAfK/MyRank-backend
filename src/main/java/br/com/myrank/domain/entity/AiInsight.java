package br.com.myrank.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Análise de perfil gerada pela IA. {@code payload} guarda o JSON estruturado
 * devolvido pelo modelo (ver {@code InsightPayloadDTO}); a linha é reaproveitada
 * enquanto o {@code selectionHash} (modelo + obras + notas) não muda.
 */
@Entity
@Table(name = "ai_insights")
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "selection_hash", nullable = false, length = 64)
    private String selectionHash;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "work_count", nullable = false)
    private int workCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public AiInsight() {}

    public AiInsight(Long userId, String selectionHash, String model, int workCount, String payload) {
        this.userId = userId;
        this.selectionHash = selectionHash;
        this.model = model;
        this.workCount = workCount;
        this.payload = payload;
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSelectionHash() { return selectionHash; }
    public void setSelectionHash(String selectionHash) { this.selectionHash = selectionHash; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getWorkCount() { return workCount; }
    public void setWorkCount(int workCount) { this.workCount = workCount; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
