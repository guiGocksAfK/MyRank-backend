package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.chat.*;
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

    // ── Conversas ──────────────────────────────────────────────────────

    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationDTO>> conversations(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(chatService.listConversations(me(ud)));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ChatConversationDTO> createGroup(
            @AuthenticationPrincipal UserDetails ud, @RequestBody CreateGroupDTO body) {
        return ResponseEntity.ok(chatService.createGroup(me(ud), body.name(), body.memberIds()));
    }

    @PostMapping("/direct/{userId}")
    public ResponseEntity<ChatConversationDTO> startDirect(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long userId) {
        return ResponseEntity.ok(chatService.startDirect(me(ud), userId));
    }

    @PatchMapping("/conversations/{id}")
    public ResponseEntity<ChatConversationDTO> rename(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody RenameConversationDTO body) {
        return ResponseEntity.ok(chatService.rename(me(ud), id, body.name()));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        chatService.deleteConversation(me(ud), id);
        return ResponseEntity.noContent().build();
    }

    // ── Mensagens ──────────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<ChatMessageDTO>> messages(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatService.history(me(ud), id, page, size));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ChatMessageDTO> send(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody SendMessageDTO body) {
        return ResponseEntity.ok(chatService.send(me(ud), id, body.body()));
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        chatService.markRead(me(ud), id);
        return ResponseEntity.noContent().build();
    }

    // ── Membros ────────────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/members")
    public ResponseEntity<List<ConversationMemberDTO>> members(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(chatService.listMembers(me(ud), id));
    }

    @PostMapping("/conversations/{id}/members")
    public ResponseEntity<List<ConversationMemberDTO>> addMembers(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody AddMembersDTO body) {
        return ResponseEntity.ok(chatService.addMembers(me(ud), id, body.userIds()));
    }

    @DeleteMapping("/conversations/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @PathVariable Long userId) {
        chatService.removeMember(me(ud), id, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Contador ───────────────────────────────────────────────────────

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(Map.of("count", chatService.unreadTotal(me(ud))));
    }

    private Long me(UserDetails ud) {
        User user = authUtils.getUser(ud);
        return user.getId();
    }
}
