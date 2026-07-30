package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.auth.LoginRequestDTO;
import br.com.myrank.dto.auth.LoginResponseDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.security.JwtService;
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

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO dto) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
            );
        } catch (Exception e) {
            throw new BadCredentialsException("Email ou senha inválidos.");
        }

        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos."));

        String token = jwtService.generateToken(user.getEmail());
        return ResponseEntity.ok(new LoginResponseDTO(token, user.getUsername()));
    }
}