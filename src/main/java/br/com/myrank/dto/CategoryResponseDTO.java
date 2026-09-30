package br.com.myrank.dto;

import java.time.LocalDateTime;
import java.util.List;
import br.com.myrank.domain.enums.TableTemplate;

public class CategoryResponseDTO {

    private Long id;
    private String name;
    private TableTemplate template;
    private boolean isDefault;
    private LocalDateTime createdAt;
    private List<SubcategoryDTO> subcategories = List.of();

    public CategoryResponseDTO() {}

    public CategoryResponseDTO(Long id, String name, TableTemplate template, boolean isDefault, LocalDateTime createdAt,
                               List<SubcategoryDTO> subcategories) {
        this.id = id;
        this.name = name;
        this.template = template;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
        this.subcategories = subcategories;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public TableTemplate getTemplate() { return template; }
    public void setTemplate(TableTemplate template) { this.template = template; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<SubcategoryDTO> getSubcategories() { return subcategories; }
    public void setSubcategories(List<SubcategoryDTO> subcategories) { this.subcategories = subcategories; }
}
