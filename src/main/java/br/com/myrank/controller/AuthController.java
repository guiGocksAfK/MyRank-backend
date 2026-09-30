package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.auth.CodeVerifyRequestDTO;
import br.com.myrank.dto.auth.EmailVerifyRequestDTO;
import br.com.myrank.dto.auth.ForgotPasswordRequestDTO;
import br.com.myrank.dto.auth.ForgotPasswordResponseDTO;
import br.com.myrank.dto.auth.LoginRequestDTO;
import br.com.myrank.dto.auth.LoginResponseDTO;
import br.com.myrank.dto.auth.OAuthCodeRequestDTO;
import br.com.myrank.dto.auth.OAuthTokenRequestDTO;
import br.com.myrank.dto.auth.PassResponseDTO;
import br.com.myrank.dto.auth.ResendVerificationRequestDTO;
import br.com.myrank.dto.auth.ResetPasswordRequestDTO;
import br.com.myrank.dto.auth.SignupCodeRequestDTO;
import br.com.myrank.exception.EmailNotVerifiedException;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.security.JwtService;
import br.com.myrank.service.EmailVerificationService;
import br.com.myrank.service.OAuthService;
import br.com.myrank.service.PasswordResetService;
import br.com.myrank.service.SignupCodeService;
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
    private final PasswordResetService passwordResetService;
    private final SignupCodeService signupCodeService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            OAuthService oAuthService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService,
            SignupCodeService signupCodeService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.oAuthService = oAuthService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.signupCodeService = signupCodeService;
    }

    /** Cadastro, etapa 1: manda o código de 6 dígitos pro email. */
    @PostMapping("/signup/code")
    public ResponseEntity<Void> sendSignupCode(@Valid @RequestBody SignupCodeRequestDTO dto) {
        signupCodeService.sendCode(dto.email(), dto.language());
        return ResponseEntity.noContent().build();
    }

    /** Cadastro, etapa 2: confere o código e devolve o passe que libera a etapa 3. */
    @PostMapping("/signup/verify")
    public ResponseEntity<PassResponseDTO> verifySignupCode(@Valid @RequestBody CodeVerifyRequestDTO dto) {
        return ResponseEntity.ok(new PassResponseDTO(signupCodeService.verifyCode(dto.email(), dto.code())));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO dto) {
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos."));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BadCredentialsException("Email ou senha inválidos.");
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

        String token = jwtService.generateToken(user.getId());
        return ResponseEntity.ok(new LoginResponseDTO(token, user.getUsername()));
    }

    /** Clique no link do email (contas pendentes do cadastro antigo): confirma e já devolve a sessão. */
    @PostMapping("/verify-email")
    public ResponseEntity<LoginResponseDTO> verifyEmail(@Valid @RequestBody EmailVerifyRequestDTO dto) {
        User user = emailVerificationService.verify(dto.token());
        String token = jwtService.generateToken(user.getId());
        return ResponseEntity.ok(new LoginResponseDTO(token, user.getUsername()));
    }

    /** Sempre 204, exista a conta ou não — não serve pra descobrir emails cadastrados. */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequestDTO dto) {
        emailVerificationService.resend(dto.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * "Esqueci minha senha", etapa 1: manda o código. Conta com senha ou email sem
     * conta: SENT (mesma resposta). Conta só com Google/Discord: SOCIAL + provedor,
     * pra tela avisar na hora.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponseDTO> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO dto) {
        return ResponseEntity.ok(passwordResetService.request(dto.email()));
    }

    /** "Esqueci minha senha", etapa 2: confere o código e devolve o passe da troca. */
    @PostMapping("/forgot-password/verify")
    public ResponseEntity<PassResponseDTO> verifyResetCode(@Valid @RequestBody CodeVerifyRequestDTO dto) {
        return ResponseEntity.ok(new PassResponseDTO(passwordResetService.verifyCode(dto.email(), dto.code())));
    }

    /** "Esqueci minha senha", etapa 3: senha nova, e a pessoa já sai logada. */
    @PostMapping("/reset-password")
    public ResponseEntity<LoginResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO dto) {
        User user = passwordResetService.reset(dto.resetPass(), dto.password());
        return ResponseEntity.ok(new LoginResponseDTO(jwtService.generateToken(user.getId()), user.getUsername()));
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
