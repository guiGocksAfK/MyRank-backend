package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.NotificationDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.social.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthUtils authUtils;

    public NotificationController(NotificationService notificationService, AuthUtils authUtils) {
        this.notificationService = notificationService;
        this.authUtils = authUtils;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> list(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.list(me(ud), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(me(ud))));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal UserDetails ud) {
        notificationService.markAllRead(me(ud));
        return ResponseEntity.noContent().build();
    }

    private Long me(UserDetails ud) {
        User user = authUtils.getUser(ud);
        return user.getId();
    }
}
