package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.auth.EmailVerifyRequestDTO;
import br.com.myrank.dto.auth.LoginRequestDTO;
import br.com.myrank.dto.auth.LoginResponseDTO;
import br.com.myrank.dto.auth.OAuthCodeRequestDTO;
import br.com.myrank.dto.auth.OAuthTokenRequestDTO;
import br.com.myrank.dto.auth.ResendVerificationRequestDTO;
import br.com.myrank.exception.EmailNotVerifiedException;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.security.JwtService;
import br.com.myrank.service.EmailVerificationService;
import br.com.myrank.service.OAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final OAuthService oAuthService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            OAuthService oAuthService,
            EmailVerificationService emailVerificationService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.oAuthService = oAuthService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO dto) {
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos."));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BadCredentialsException("Esta conta usa login social. Entre com Google ou Discord.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
            );
        } catch (Exception e) {
            throw new BadCredentialsException("Email ou senha inválidos.");
        }

        // Só depois da senha conferir: senão o endpoint revelaria quais emails têm conta pendente.
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        String token = jwtService.generateToken(user.getEmail());
        return ResponseEntity.ok(new LoginResponseDTO(token, user.getUsername()));
    }

    /** Clique no link do email: confirma a conta e já devolve a sessão. */
    @PostMapping("/verify-email")
    public ResponseEntity<LoginResponseDTO> verifyEmail(@Valid @RequestBody EmailVerifyRequestDTO dto) {
        User user = emailVerificationService.verify(dto.token());
        String token = jwtService.generateToken(user.getEmail());
        return ResponseEntity.ok(new LoginResponseDTO(token, user.getUsername()));
    }

    /** Sempre 204, exista a conta ou não — não serve pra descobrir emails cadastrados. */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequestDTO dto) {
        emailVerificationService.resend(dto.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<LoginResponseDTO> loginWithGoogle(@Valid @RequestBody OAuthTokenRequestDTO dto) {
        return ResponseEntity.ok(oAuthService.loginWithGoogle(dto.token()));
    }

    @PostMapping("/oauth/discord")
    public ResponseEntity<LoginResponseDTO> loginWithDiscord(@Valid @RequestBody OAuthTokenRequestDTO dto) {
        return ResponseEntity.ok(oAuthService.loginWithDiscord(dto.token()));
    }

    @PostMapping("/oauth/discord/callback")
    public ResponseEntity<LoginResponseDTO> loginWithDiscordCallback(@Valid @RequestBody OAuthCodeRequestDTO dto) {
        return ResponseEntity.ok(oAuthService.loginWithDiscordCode(dto.code(), dto.redirectUri()));
    }
}
