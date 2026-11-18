package br.com.myrank.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;
import br.com.myrank.domain.enums.TableTemplate;

public record WorkCreateDTO(
        @NotNull(message = "Escolha a tabela.") Long categoryId,

        @NotBlank(message = "Informe o título.")
        @Size(max = 300, message = "O título é longo demais.")
        String title,

        @Size(max = 1000, message = "O link da capa é longo demais.") String imageUrl,
        @Size(max = 200, message = "O criador passa de 200 caracteres.") String creator,
        LocalDate releaseDate,

        @Min(value = 0, message = "O tempo não pode ser negativo.") @Max(value = 1_000_000, message = "O tempo informado é grande demais.") int timeMinutes,
        @DecimalMin(value = "0.0", message = "A nota precisa ser de 0 a 10.") @DecimalMax(value = "10.0", message = "A nota precisa ser de 0 a 10.") double score,
        /** Opcional: subcategoria da mesma tabela. */
        Long subcategoryId,
        TableTemplate template,
        Map<String, Object> details
) {}
