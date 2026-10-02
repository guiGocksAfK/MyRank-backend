package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.domain.enums.OnboardingStep;
import br.com.myrank.dto.UserResponseDTO;
import br.com.myrank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceOnboardingTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users, mock(PasswordEncoder.class));

    @ParameterizedTest
    @EnumSource(value = AuthProvider.class, names = {"GOOGLE", "DISCORD"})
    void novaContaSocial_nascePrivadaEmTables(AuthProvider provider) {
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service.findOrCreateFromOAuth(info(), provider);

        assertThat(user.getOnboardingStep()).isEqualTo(OnboardingStep.TABLES);
        assertThat(user.isPublic()).isFalse();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getUserStats()).isNotNull();
        assertThat(user.getAuthProvider()).isEqualTo(provider);
        assertThat(UserResponseDTO.fromEntity(user).onboardingStep()).isEqualTo(OnboardingStep.TABLES);
    }

    @ParameterizedTest
    @EnumSource(value = AuthProvider.class, names = {"GOOGLE", "DISCORD"})
    void loginSocial_existentePreservaEtapaEPrivacidade(AuthProvider provider) {
        User existing = existingUser(provider);
        existing.setOnboardingStep(OnboardingStep.FIRST_WORK);
        when(users.findByAuthProviderAndProviderId(provider, "social-1")).thenReturn(Optional.of(existing));
        when(users.save(existing)).thenReturn(existing);

        assertThat(service.findOrCreateFromOAuth(info(), provider)).isSameAs(existing);
        assertThat(existing.getOnboardingStep()).isEqualTo(OnboardingStep.FIRST_WORK);
        assertThat(existing.isPublic()).isTrue();
    }

    @Test
    void vincularOAuthEmContaLocal_preservaPerfilEEtapaDone() {
        User existing = existingUser(AuthProvider.LOCAL);
        when(users.findByEmail("social@myrank.dev")).thenReturn(Optional.of(existing));
        when(users.save(existing)).thenReturn(existing);

        User user = service.findOrCreateFromOAuth(info(), AuthProvider.GOOGLE);

        assertThat(user.getOnboardingStep()).isEqualTo(OnboardingStep.DONE);
        assertThat(user.isPublic()).isTrue();
    }

    private OAuthUserInfo info() {
        return new OAuthUserInfo("social-1", "social@myrank.dev", "social", null);
    }

    private User existingUser(AuthProvider provider) {
        User user = new User();
        user.setId(1L);
        user.setAuthProvider(provider);
        user.setEmailVerified(true);
        user.setPublic(true);
        return user;
    }
}
