package br.com.myrank.dto;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.OnboardingStep;

import java.time.LocalDateTime;

public record UserResponseDTO(
        Long id,
        String username,
        String email,
        String avatarUrl,
        String bio,
        String plan,
        boolean isPublic,
        String language,
        LocalDateTime createdAt,
        /** Conta só com Google/Discord não tem senha — a exclusão não pede senha. */
        boolean hasPassword,
        OnboardingStep onboardingStep,
        /** LOCAL (email e senha) | GOOGLE | DISCORD. */
        String authProvider,
        String dmPolicy,
        java.util.List<String> mutedNotifications,
        boolean weightedByDefault
) {
    public static UserResponseDTO fromEntity(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getPlan().name(),
                user.isPublic(),
                user.getLanguage(),
                user.getCreatedAt(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank(),
                user.getOnboardingStep(),
                user.getAuthProvider() != null ? user.getAuthProvider().name() : "LOCAL",
                user.getDmPolicy().name(),
                user.getMutedGroups().stream().map(Enum::name).sorted().toList(),
                user.isWeightedByDefault()
        );
    }
}
