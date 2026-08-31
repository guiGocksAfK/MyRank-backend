package br.com.myrank.dto;

import java.util.List;
import java.util.Map;

/**
 * Perfil completo (perfil público, ou privado que o viewer já segue / é o dono).
 * Quando {@code locked} é true o perfil é privado e o viewer não segue: só
 * username/avatar/bio e as contagens vêm preenchidos; listas ficam vazias.
 */
public record SocialProfileDTO(
        Long id,
        String username,
        String avatarUrl,
        String bio,
        String plan,
        int worksCount,
        double avgScore,
        boolean following,
        boolean followsYou,
        boolean isPublic,
        /** eu já pedi pra seguir (pendente). */
        boolean requested,
        /** perfil privado e o viewer não segue → conteúdo omitido. */
        boolean locked,
        long followerCount,
        long followingCount,
        List<WorkMiniDTO> top,
        Map<String, Integer> breakdown,
        List<BadgeMiniDTO> badges,
        List<WorkMiniDTO> works
) {}
