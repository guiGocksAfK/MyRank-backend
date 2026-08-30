package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.ChatConversationDTO;
import br.com.myrank.dto.ChatMessageDTO;
import br.com.myrank.dto.SendMessageDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.social.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final AuthUtils authUtils;

    public ChatController(ChatService chatService, AuthUtils authUtils) {
        this.chatService = chatService;
        this.authUtils = authUtils;
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationDTO>> conversations(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(chatService.inbox(me(ud)));
    }

    @GetMapping("/with/{userId}")
    public ResponseEntity<List<ChatMessageDTO>> conversation(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatService.conversation(me(ud), userId, page, size));
    }

    @PostMapping("/with/{userId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long userId) {
        chatService.markRead(me(ud), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageDTO> send(
            @AuthenticationPrincipal UserDetails ud, @RequestBody SendMessageDTO body) {
        return ResponseEntity.ok(chatService.send(me(ud), body.recipientId(), body.body()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(Map.of("count", chatService.unreadTotal(me(ud))));
    }

    private Long me(UserDetails ud) {
        User user = authUtils.getUser(ud);
        return user.getId();
    }
}
