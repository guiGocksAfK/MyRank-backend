package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.UserStats;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.dto.UserCreateDTO;
import br.com.myrank.dto.UserUpdateDTO;
import br.com.myrank.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, CategoryService categoryService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
    }

    public User createUser(UserCreateDTO dto) {
        if (userRepository.existsByUsername(dto.username())) {
            throw new IllegalArgumentException("Username já está em uso.");
        }
        if (dto.email() != null && userRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("Email já está em uso.");
        }

        User user = new User();
        user.setUsername(dto.username());
        user.setEmail(dto.email());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setAuthProvider(AuthProvider.LOCAL);

        UserStats stats = new UserStats(user);
        user.setUserStats(stats);

        User savedUser = userRepository.save(user);

        categoryService.createDefaultCategories(savedUser);

        return savedUser;
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
    }

    public User updateUser(Long id, UserUpdateDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (dto.username() != null && !dto.username().isBlank()) {
            if (userRepository.existsByUsernameAndIdNot(dto.username(), id)) {
                throw new IllegalArgumentException("Username já está em uso.");
            }
            user.setUsername(dto.username());
        }

        if (dto.bio() != null) {
            user.setBio(dto.bio());
        }

        if (dto.isPublic() != null) {
            user.setPublic(dto.isPublic());
        }

        if (dto.language() != null) {
            String lang = dto.language().trim().toUpperCase();
            if (!java.util.Set.of("PT", "EN", "ES").contains(lang)) {
                throw new IllegalArgumentException("Idioma inválido.");
            }
            user.setLanguage(lang);
        }

        if (dto.avatarUrl() != null) {
            String url = dto.avatarUrl().trim();
            if (url.isEmpty()) {
                user.setAvatarUrl(null);
            } else {
                if (!url.startsWith("https://")) {
                    throw new IllegalArgumentException("A URL da foto precisa começar com https://");
                }
                if (url.length() > 1000) {
                    throw new IllegalArgumentException("A URL da foto é longa demais.");
                }
                user.setAvatarUrl(url);
            }
        }

        return userRepository.save(user);
    }

    public User findOrCreateFromOAuth(OAuthUserInfo info, AuthProvider provider) {
        return userRepository.findByAuthProviderAndProviderId(provider, info.providerId())
                .map(user -> userRepository.save(updateOAuthProfile(user, info, provider)))
                .orElseGet(() -> createOrLinkOAuthUser(info, provider));
    }

    /**
     * Grava o snowflake do Discord em users.discord_id — sempre que o login vier do
     * Discord, inclusive numa conta LOCAL/GOOGLE. É o que resolve o caso híbrido: o
     * auth_provider continua sendo o original (o método de login não muda), mas
     * passamos a saber qual Discord é aquele usuário — que é o que o bot precisa.
     */
    private void linkDiscordId(User user, OAuthUserInfo info, AuthProvider provider) {
        if (provider != AuthProvider.DISCORD) {
            return;
        }
        String discordId = info.providerId();
        if (discordId == null || discordId.isBlank()) {
            return;
        }
        if (discordId.equals(user.getDiscordId())) {
            return;
        }

        // O índice único rejeitaria com 500; aqui a mensagem é acionável.
        userRepository.findByDiscordId(discordId).ifPresent(owner -> {
            if (!owner.getId().equals(user.getId())) {
                throw new IllegalArgumentException(
                        "Esta conta do Discord já está vinculada a outro usuário do MyRank.");
            }
        });

        user.setDiscordId(discordId);
    }

    private User createOrLinkOAuthUser(OAuthUserInfo info, AuthProvider provider) {
        if (info.email() != null && !info.email().isBlank()) {
            Optional<User> existingByEmail = userRepository.findByEmail(info.email());
            if (existingByEmail.isPresent()) {
                User user = existingByEmail.get();
                if (user.getAuthProvider() != AuthProvider.LOCAL && user.getAuthProvider() != provider) {
                    throw new IllegalArgumentException("Email já cadastrado com outro método de login.");
                }

                user.setProviderId(info.providerId());
                return userRepository.save(updateOAuthProfile(user, info, provider));
            }
        }

        User user = new User();
        user.setUsername(generateUniqueUsername(info.usernameBase()));
        user.setEmail(info.email());
        user.setAuthProvider(provider);
        user.setProviderId(info.providerId());
        user.setAvatarUrl(info.avatarUrl());
        linkDiscordId(user, info, provider);

        UserStats stats = new UserStats(user);
        user.setUserStats(stats);

        User savedUser = userRepository.save(user);
        categoryService.createDefaultCategories(savedUser);
        return savedUser;
    }

    private User updateOAuthProfile(User user, OAuthUserInfo info, AuthProvider provider) {
        linkDiscordId(user, info, provider);

        // só preenche a partir do OAuth se o usuário ainda não escolheu uma foto —
        // assim a URL definida por ele não é sobrescrita a cada login
        boolean hasOwnPhoto = user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank();
        if (!hasOwnPhoto && info.avatarUrl() != null && !info.avatarUrl().isBlank()) {
            user.setAvatarUrl(info.avatarUrl());
        }
        return user;
    }

    private String generateUniqueUsername(String base) {
        String sanitized = base.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (sanitized.length() < 3) {
            sanitized = "user" + sanitized;
        }
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        String username = sanitized;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            String suffix = String.valueOf(counter++);
            int maxBaseLength = 50 - suffix.length();
            username = sanitized.substring(0, Math.min(sanitized.length(), maxBaseLength)) + suffix;
        }
        return username;
    }
}