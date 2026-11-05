package br.com.myrank.dto.creator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edição de um criador. {@code photoUrl} vazio/nulo = volta pra foto automática;
 * {@code type} nulo = volta pro tipo deduzido das obras.
 */
public record CreatorUpdateDTO(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000, message = "O link da foto é longo demais.") String photoUrl,
        @Pattern(regexp = "Diretor|Escritor|Studio|Criador") String type,
        @Pattern(regexp = "PERSON|COMPANY") String kind
) {}
