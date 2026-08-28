package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.BadgeResponseDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.badge.BadgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeService badgeService;
    private final AuthUtils authUtils;

    public BadgeController(BadgeService badgeService, AuthUtils authUtils) {
        this.badgeService = badgeService;
        this.authUtils = authUtils;
    }

    /** Catálogo completo de badges com o progresso do usuário logado. */
    @GetMapping
    public ResponseEntity<List<BadgeResponseDTO>> myBadges(@AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(badgeService.listForUser(user.getId()));
    }
}
