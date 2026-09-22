package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.dto.UserCreateDTO;
import br.com.myrank.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de cadastro/junção que fecham o pre-account takeover por email. */
class UserServiceEmailVerificationTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, passwordEncoder, categoryService, emailVerificationService);
        when(passwordEncoder.encode(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User localUser(boolean verified) {
        User user = new User();
        user.setId(7L);
        user.setUsername("dono");
        user.setEmail("dono@myrank.dev");
        user.setPasswordHash("hash:senha-antiga");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(verified);
        return user;
    }

    @Test
    void cadastroNovo_nasceNaoConfirmado_eMandaOLink() {
        when(userRepository.findByEmail("novo@myrank.dev")).thenReturn(Optional.empty());

        User user = service.createUser(new UserCreateDTO("novo", "novo@myrank.dev", "senha1234", "en"));

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getLanguage()).isEqualTo("EN");
        verify(categoryService).createDefaultCategories(user);
        verify(emailVerificationService).issueAndSend(user);
    }

    @Test
    void cadastroPendenteComMesmoEmail_podeSerRefeito() {
        User pending = localUser(false);
        when(userRepository.findByEmail("dono@myrank.dev")).thenReturn(Optional.of(pending));

        User user = service.createUser(new UserCreateDTO("outronome", "dono@myrank.dev", "senha-nova", null));

        assertThat(user).isSameAs(pending);
        assertThat(user.getUsername()).isEqualTo("outronome");
        assertThat(user.getPasswordHash()).isEqualTo("hash:senha-nova");
        verify(categoryService, never()).createDefaultCategories(any());
        verify(emailVerificationService).issueAndSend(pending);
    }

    @Test
    void emailDeContaConfirmada_continuaBloqueado() {
        when(userRepository.findByEmail("dono@myrank.dev")).thenReturn(Optional.of(localUser(true)));
        when(userRepository.existsByEmail("dono@myrank.dev")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(new UserCreateDTO("x", "dono@myrank.dev", "senha1234", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email já está em uso.");
    }

    @Test
    void loginSocialEmContaPendente_descartaASenha_eConfirma() {
        User pending = localUser(false);
        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "g-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("dono@myrank.dev")).thenReturn(Optional.of(pending));

        User user = service.findOrCreateFromOAuth(
                new OAuthUserInfo("g-1", "dono@myrank.dev", "dono", null), AuthProvider.GOOGLE);

        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void loginSocialEmContaConfirmada_mantemASenha() {
        User verified = localUser(true);
        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "g-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("dono@myrank.dev")).thenReturn(Optional.of(verified));

        User user = service.findOrCreateFromOAuth(
                new OAuthUserInfo("g-1", "dono@myrank.dev", "dono", null), AuthProvider.GOOGLE);

        assertThat(user.getPasswordHash()).isEqualTo("hash:senha-antiga");
    }
}
