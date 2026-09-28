package br.com.myrank.dto;

import br.com.myrank.domain.entity.Subcategory;

public record SubcategoryDTO(Long id, String name) {

    public static SubcategoryDTO fromEntity(Subcategory subcategory) {
        return new SubcategoryDTO(subcategory.getId(), subcategory.getName());
    }
}
