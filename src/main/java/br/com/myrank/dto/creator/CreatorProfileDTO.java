package br.com.myrank.dto.creator;

/**
 * Perfil de um criador pro usuário. {@code photoUrl} é a que aparece (a dele, se
 * editou; senão a automática); {@code customPhoto} diz se é a dele. {@code type} só vem
 * quando ele escolheu um tipo. {@code pending}: a foto automática ainda não foi
 * procurada (a tela pede de novo depois).
 */
public record CreatorProfileDTO(
        String key,
        String photoUrl,
        boolean customPhoto,
        String type,
        boolean pending
) {}
