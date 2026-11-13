package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.auth.CodeVerifyRequestDTO;
import br.com.myrank.dto.auth.ForgotPasswordRequestDTO;
import br.com.myrank.dto.auth.ForgotPasswordResponseDTO;
import br.com.myrank.dto.auth.GoogleCodeRequestDTO;
import br.com.myrank.dto.auth.LoginRequestDTO;
import br.com.myrank.dto.auth.LoginResponseDTO;
import br.com.myrank.dto.auth.OAuthCodeRequestDTO;
import br.com.myrank.dto.auth.OAuthTokenRequestDTO;
import br.com.myrank.dto.auth.PassResponseDTO;
import br.com.myrank.dto.auth.ResetPasswordRequestDTO;
import br.com.myrank.dto.auth.SignupCodeRequestDTO;
import br.com.myrank.exception.EmailNotVerifiedException;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.security.JwtService;
import br.com.myrank.security.SessionCookie;
import br.com.myrank.service.OAuthService;
import br.com.myrank.service.PasswordResetService;
import br.com.myrank.service.SignupCodeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
    private final PasswordResetService passwordResetService;
    private final SignupCodeService signupCodeService;
    private final SessionCookie sessionCookie;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            OAuthService oAuthService,
            PasswordResetService passwordResetService,
            SignupCodeService signupCodeService,
            SessionCookie sessionCookie
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.oAuthService = oAuthService;
        this.passwordResetService = passwordResetService;
        this.signupCodeService = signupCodeService;
        this.sessionCookie = sessionCookie;
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

        String token = jwtService.generateToken(user);
        return sessionCookie.signedIn(new LoginResponseDTO(token, user.getUsername()));
    }

    /**
     * Sair: apaga o cookie da sessão neste navegador. O JavaScript do front não
     * consegue apagar um cookie HttpOnly, por isso precisa desta rota. Os outros
     * aparelhos continuam logados (pra derrubar todos existe "sair dos outros aparelhos").
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie.clearHeader())
                .build();
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
        return sessionCookie.signedIn(new LoginResponseDTO(jwtService.generateToken(user), user.getUsername()));
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<LoginResponseDTO> loginWithGoogle(@Valid @RequestBody OAuthTokenRequestDTO dto) {
        return sessionCookie.signedIn(oAuthService.loginWithGoogle(dto.token()));
    }

    /**
     * Google pelo botão do próprio site: chega o código de autorização da janela do
     * Google e o back troca pelo token. A rota de cima (token pronto) fica pro botão
     * oficial do Google, que entrega o token direto.
     */
    @PostMapping("/oauth/google/callback")
    public ResponseEntity<LoginResponseDTO> loginWithGoogleCallback(@Valid @RequestBody GoogleCodeRequestDTO dto) {
        return sessionCookie.signedIn(oAuthService.loginWithGoogleCode(dto.code()));
    }

    /**
     * Discord só entra pelo código de autorização: quem troca o código pelo token é o
     * back, com o client secret, então o token é sempre do MyRank. A rota que recebia o
     * access_token pronto saiu porque aceitava token emitido para qualquer outro app.
     */
    @PostMapping("/oauth/discord/callback")
    public ResponseEntity<LoginResponseDTO> loginWithDiscordCallback(@Valid @RequestBody OAuthCodeRequestDTO dto) {
        return sessionCookie.signedIn(oAuthService.loginWithDiscordCode(dto.code(), dto.redirectUri()));
    }
}
