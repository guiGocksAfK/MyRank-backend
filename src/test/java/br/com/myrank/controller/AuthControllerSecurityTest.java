package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.auth.LoginRequestDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.security.JwtService;
import br.com.myrank.security.SessionCookie;
import br.com.myrank.service.SignupCodeService;
import br.com.myrank.service.OAuthService;
import br.com.myrank.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerSecurityTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AuthController controller = new AuthController(
            mock(AuthenticationManager.class), mock(JwtService.class), users,
            mock(OAuthService.class),
            mock(PasswordResetService.class), mock(SignupCodeService.class),
            new SessionCookie(true, "Strict", "", 86_400_000L, "http://localhost:5173"));

    @Test
    void socialAndUnknownEmailsHaveSameLoginError() {
        User social = new User();
        social.setEmail("social@example.com");
        when(users.findByEmail("social@example.com")).thenReturn(Optional.of(social));
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        BadCredentialsException socialError = catchThrowableOfType(
                () -> controller.login(new LoginRequestDTO("social@example.com", "wrong")),
                BadCredentialsException.class);
        BadCredentialsException unknownError = catchThrowableOfType(
                () -> controller.login(new LoginRequestDTO("unknown@example.com", "wrong")),
                BadCredentialsException.class);

        assertThat(socialError.getMessage()).isEqualTo(unknownError.getMessage());
    }

    @Test
    void sair_apagaOCookieDaSessao() {
        ResponseEntity<Void> response = controller.logout();

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith(SessionCookie.NAME + "=;")
                .contains("Max-Age=0");
    }
}
