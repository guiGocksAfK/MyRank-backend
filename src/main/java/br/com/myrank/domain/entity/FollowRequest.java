package br.com.myrank.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Pedido pendente pra seguir um perfil privado. Aprovar cria a linha em
 * {@code follow} e apaga o pedido; recusar/cancelar apenas apaga.
 */
@Entity
@Table(name = "follow_request")
public class FollowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public FollowRequest() {}

    public FollowRequest(Long requesterId, Long targetId) {
        this.requesterId = requesterId;
        this.targetId = targetId;
    }

    public Long getId() { return id; }
    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
