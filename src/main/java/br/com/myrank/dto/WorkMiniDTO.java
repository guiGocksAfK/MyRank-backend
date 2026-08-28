package br.com.myrank.dto;

import java.math.BigDecimal;

public record WorkMiniDTO(
        Long id,
        String title,
        String type,
        String imageUrl,
        BigDecimal score
) {}
