package br.com.myrank.domain.entity;

import br.com.myrank.domain.enums.TableTemplate;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Um ou mais templates, na ordem escolhida (V9, category_templates). Cada obra
     * da tabela usa um deles. EAGER + BatchSize: toda tela que mostra a tabela
     * precisa deles, e o lote evita uma query por tabela.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_templates", joinColumns = @JoinColumn(name = "category_id"))
    @OrderColumn(name = "position")
    @Enumerated(EnumType.STRING)
    @Column(name = "template", nullable = false, length = 32)
    @BatchSize(size = 100)
    private List<TableTemplate> templates = new ArrayList<>();

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Category() {}

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<TableTemplate> getTemplates() { return templates; }
    public void setTemplates(List<TableTemplate> templates) {
        this.templates.clear();
        this.templates.addAll(templates);
    }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
