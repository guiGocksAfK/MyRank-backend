package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.UserAvatar;
import br.com.myrank.dto.UserCreateDTO;
import br.com.myrank.dto.UserResponseDTO;
import br.com.myrank.dto.UserUpdateDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.UserAvatarService;
import br.com.myrank.service.UserService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserAvatarService userAvatarService;
    private final AuthUtils authUtils;

    public UserController(UserService userService, UserAvatarService userAvatarService, AuthUtils authUtils) {
        this.userService = userService;
        this.userAvatarService = userAvatarService;
        this.authUtils = authUtils;
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@RequestBody UserCreateDTO dto) {
        User user = userService.createUser(dto);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getById(@PathVariable Long id) {
        User user = userService.getUserById(id);
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

    /** Envia/substitui a foto de perfil (PNG, JPEG ou WebP, até 1 MB). */
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        User user = authUtils.getUser(userDetails);
        userAvatarService.upload(user, file);
        return ResponseEntity.noContent().build();
    }

    /** Remove a foto de perfil. */
    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        userAvatarService.delete(user.getId());
        return ResponseEntity.noContent().build();
    }

    /** Serve a imagem da foto de perfil. Endpoint público (usado direto em &lt;img&gt;). */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long id) {
        UserAvatar avatar;
        try {
            avatar = userAvatarService.get(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.getContentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(avatar.getImage());
    }
}