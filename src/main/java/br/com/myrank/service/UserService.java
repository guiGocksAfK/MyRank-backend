package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.UserStats;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.dto.UserCreateDTO;
import br.com.myrank.dto.UserResponseDTO;
import br.com.myrank.dto.UserUpdateDTO;
import br.com.myrank.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
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

        return userRepository.save(user);
    }
}