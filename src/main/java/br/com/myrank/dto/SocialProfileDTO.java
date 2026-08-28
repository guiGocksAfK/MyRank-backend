package br.com.myrank.dto;

import java.util.List;
import java.util.Map;

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
        List<WorkMiniDTO> top,
        Map<String, Integer> breakdown,
        List<BadgeMiniDTO> badges,
        List<WorkMiniDTO> works
) {}
