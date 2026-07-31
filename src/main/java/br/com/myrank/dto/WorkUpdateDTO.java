package br.com.myrank.dto;

import java.time.LocalDate;

public record WorkUpdateDTO(
        String title,
        String imageUrl,
        String creator,
        LocalDate releaseDate,
        Integer timeMinutes,
        Double score
) {}