package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.UserCreateDTO;
import br.com.myrank.dto.UserResponseDTO;
import br.com.myrank.dto.UserUpdateDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthUtils authUtils;

    public UserController(UserService userService, AuthUtils authUtils) {
        this.userService = userService;
        this.authUtils = authUtils;
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@RequestBody UserCreateDTO dto) {
        User user = userService.createUser(dto);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMe(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserUpdateDTO dto) {
        User user = authUtils.getUser(userDetails);
        User updated = userService.updateUser(user.getId(), dto);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(updated));
    }
}
