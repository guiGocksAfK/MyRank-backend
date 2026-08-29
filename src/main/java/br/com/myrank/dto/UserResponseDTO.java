package br.com.myrank.dto;

import br.com.myrank.domain.entity.User;

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
        LocalDateTime createdAt
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
                user.getCreatedAt()
        );
    }
}