package br.com.myrank.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;
import br.com.myrank.domain.enums.TableTemplate;

/** Update parcial: campo null = não mexe. */
public record WorkUpdateDTO(
        @Size(max = 300, message = "O título é longo demais.")
        String title,

        @Size(max = 1000, message = "O link da capa é longo demais.") String imageUrl,
        @Size(max = 200, message = "O criador passa de 200 caracteres.") String creator,
        LocalDate releaseDate,

        @Min(value = 0, message = "O tempo não pode ser negativo.") @Max(value = 1_000_000, message = "O tempo informado é grande demais.") Integer timeMinutes,
        @DecimalMin(value = "0.0", message = "A nota precisa ser de 0 a 10.") @DecimalMax(value = "10.0", message = "A nota precisa ser de 0 a 10.") Double score,
        /** null = não mexe; 0 = tira da subcategoria; outro = move pra essa subcategoria. */
        Long subcategoryId,
        TableTemplate template,
        /** null mantém; objeto substitui os detalhes; {} limpa. */
        Map<String, Object> details
) {}
