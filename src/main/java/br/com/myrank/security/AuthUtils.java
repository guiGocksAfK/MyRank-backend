package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthUtils {

    private final UserRepository userRepository;

    public AuthUtils(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Resolve o User real a partir do UserDetails autenticado.
     * Quando vem do filtro JWT/Bot ({@link AuthenticatedUser}), devolve direto sem
     * nova query no banco; o fallback por email atende testes com {@code @WithMockUser}.
     */
    public User getUser(UserDetails userDetails) {
        if (userDetails instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.getUser();
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
    }
}