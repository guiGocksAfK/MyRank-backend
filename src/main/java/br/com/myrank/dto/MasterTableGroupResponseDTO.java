package br.com.myrank.dto;

import br.com.myrank.domain.entity.MasterTableGroup;

import java.time.LocalDateTime;
import java.util.List;

public record MasterTableGroupResponseDTO(
        Long id,
        String name,
        List<Long> categoryIds,
        LocalDateTime createdAt
) {
    public static MasterTableGroupResponseDTO fromEntity(MasterTableGroup group) {
        return new MasterTableGroupResponseDTO(
                group.getId(),
                group.getName(),
                group.getCategories().stream().map(c -> c.getId()).toList(),
                group.getCreatedAt()
        );
    }
}