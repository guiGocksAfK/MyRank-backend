package br.com.myrank.controller;

import jakarta.validation.Valid;
import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.AccountDeleteRequestDTO;
import br.com.myrank.dto.UserCreateDTO;
import br.com.myrank.dto.UserResponseDTO;
import br.com.myrank.dto.UserUpdateDTO;
import br.com.myrank.dto.auth.LoginResponseDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.security.JwtService;
import br.com.myrank.security.SessionCookie;
import br.com.myrank.service.AccountDeletionService;
import br.com.myrank.service.SignupCodeService;
import br.com.myrank.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final AuthUtils authUtils;
    private final SignupCodeService signupCodeService;
    private final JwtService jwtService;
    private final SessionCookie sessionCookie;

    public UserController(UserService userService, AccountDeletionService accountDeletionService, AuthUtils authUtils,
                          SignupCodeService signupCodeService, JwtService jwtService,
                          SessionCookie sessionCookie) {
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
        this.authUtils = authUtils;
        this.signupCodeService = signupCodeService;
        this.jwtService = jwtService;
        this.sessionCookie = sessionCookie;
    }

    /** Última etapa do cadastro: o email vem do passe do código, e a conta já entra logada. */
    @PostMapping
    public ResponseEntity<LoginResponseDTO> create(@Valid @RequestBody UserCreateDTO dto) {
        String email = signupCodeService.emailFromPass(dto.signupPass());
        User user = userService.createUser(dto, email);
        return sessionCookie.signedIn(new LoginResponseDTO(jwtService.generateToken(user), user.getUsername()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    /** Desconecta todos os aparelhos; quem pediu continua logado com o token novo. */
    @PostMapping("/me/sessions/revoke")
    public ResponseEntity<LoginResponseDTO> revokeSessions(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.revokeSessions(authUtils.getUser(userDetails).getId());
        return sessionCookie.signedIn(new LoginResponseDTO(jwtService.generateToken(user), user.getUsername()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMe(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserUpdateDTO dto) {
        User user = authUtils.getUser(userDetails);
        User updated = userService.updateUser(user.getId(), dto);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(updated));
    }

    /** Manda o código de confirmação da exclusão pro email da conta (toda conta usa). */
    @PostMapping("/me/deletion-code")
    public ResponseEntity<Void> requestDeletionCode(@AuthenticationPrincipal UserDetails userDetails) {
        accountDeletionService.issueDeletionCode(authUtils.getUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AccountDeleteRequestDTO dto) {
        User user = authUtils.getUser(userDetails);
        accountDeletionService.deleteAccount(user, dto);
        return ResponseEntity.noContent().build();
    }
}
