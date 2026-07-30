package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.UserCreateDTO;
import br.com.myrank.dto.UserResponseDTO;
import br.com.myrank.dto.UserUpdateDTO;
import br.com.myrank.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@RequestBody UserCreateDTO dto) {
        User user = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDTO.fromEntity(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userService.getByEmail(email);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateCurrentUser(
            Authentication authentication,
            @RequestBody UserUpdateDTO dto
    ) {
        String email = authentication.getName();
        User currentUser = userService.getByEmail(email);
        User updated = userService.updateUser(currentUser.getId(), dto);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(updated));
    }
}