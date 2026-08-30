package br.com.myrank.domain.entity;

import br.com.myrank.domain.enums.ConversationAccess;
import br.com.myrank.domain.enums.ConversationType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** DM (DIRECT, 2 membros) ou grupo (GROUP, nome + foto + N membros + cargos). */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "conversation_type")
    private ConversationType type;

    /** null em DIRECT. */
    @Column(length = 80)
    private String name;

    /** Foto do grupo (URL). null = usa iniciais. */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "conversation_access")
    private ConversationAccess access = ConversationAccess.CLOSED;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Conversation() {}

    public Conversation(ConversationType type, String name, Long createdBy) {
        this.type = type;
        this.name = name;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }

    public ConversationType getType() { return type; }
    public void setType(ConversationType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public ConversationAccess getAccess() { return access; }
    public void setAccess(ConversationAccess access) { this.access = access; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
