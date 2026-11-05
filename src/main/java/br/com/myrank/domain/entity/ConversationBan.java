package br.com.myrank.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** Expulsão de um grupo: a pessoa não volta pelo diretório, por pedido nem pelo link de convite. */
@Entity
@Table(name = "conversation_bans")
@IdClass(ConversationBan.Key.class)
public class ConversationBan {

    @Id
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "banned_by")
    private Long bannedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public ConversationBan() {}

    public ConversationBan(Long conversationId, Long userId, Long bannedBy) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.bannedBy = bannedBy;
    }

    public Long getConversationId() { return conversationId; }
    public Long getUserId() { return userId; }
    public Long getBannedBy() { return bannedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public static class Key implements Serializable {
        private Long conversationId;
        private Long userId;

        public Key() {}

        public Key(Long conversationId, Long userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(conversationId, k.conversationId)
                    && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, userId);
        }
    }
}
