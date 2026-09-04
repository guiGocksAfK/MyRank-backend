package br.com.myrank.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record WorkCreateDTO(
        @NotNull Long categoryId,

        @NotBlank(message = "Informe o título.")
        @Size(max = 300, message = "O título é longo demais.")
        String title,

        @Size(max = 1000) String imageUrl,
        @Size(max = 200) String creator,
        LocalDate releaseDate,

        @Min(0) @Max(1_000_000) int timeMinutes,
        @DecimalMin("0.0") @DecimalMax("10.0") double score
) {}
