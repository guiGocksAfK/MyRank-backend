package br.com.myrank.dto;

import br.com.myrank.domain.entity.Work;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WorkResponseDTO(
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String imageUrl,
        String creator,
        LocalDate releaseDate,
        int timeMinutes,
        Integer position,
        BigDecimal score,
        BigDecimal timeBonusScore,
        BigDecimal finalScore,
        LocalDateTime createdAt
) {
    public static WorkResponseDTO fromEntity(Work work) {
        return new WorkResponseDTO(
                work.getId(),
                work.getCategory().getId(),
                work.getCategory().getName(),
                work.getTitle(),
                work.getImageUrl(),
                work.getCreator(),
                work.getReleaseDate(),
                work.getTimeMinutes(),
                work.getPosition(),
                work.getScore(),
                work.getTimeBonusScore(),
                work.getFinalScore(),
                work.getCreatedAt()
        );
    }
}