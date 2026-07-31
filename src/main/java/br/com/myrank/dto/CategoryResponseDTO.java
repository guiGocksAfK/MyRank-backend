package br.com.myrank.dto;

import java.time.LocalDateTime;

public class CategoryResponseDTO {

    private Long id;
    private String name;
    private boolean isDefault;
    private LocalDateTime createdAt;

    public CategoryResponseDTO() {}

    public CategoryResponseDTO(Long id, String name, boolean isDefault, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}