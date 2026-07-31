package br.com.myrank.dto;

import java.time.LocalDate;

public record WorkCreateDTO(
        Long categoryId,
        String title,
        String imageUrl,
        String creator,
        LocalDate releaseDate,
        int timeMinutes,
        double score
) {}