package br.com.myrank.dto;

/** Card de usuário nas listas (seguindo, sugestões, busca). */
public record SocialUserDTO(
        Long id,
        String username,
        String avatarUrl,
        String bio,
        String plan,
        int worksCount,
        double avgScore,
        boolean following,
        boolean followsYou
) {}
