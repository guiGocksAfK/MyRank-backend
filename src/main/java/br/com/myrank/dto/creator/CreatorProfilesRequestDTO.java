package br.com.myrank.dto.creator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Criadores que a tela de Criadores está mostrando, pra buscar foto e o que foi editado. */
public record CreatorProfilesRequestDTO(
        @NotNull @Size(max = 300, message = "Criadores demais de uma vez.")
        List<@Valid Item> creators
) {
    /** {@code kind}: PERSON (foto de perfil) ou COMPANY (logo de estúdio). */
    public record Item(
            @NotBlank @Size(max = 200) String name,
            @NotNull @Pattern(regexp = "PERSON|COMPANY") String kind
    ) {}
}
