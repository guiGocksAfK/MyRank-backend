package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.chat.*;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.social.ChatRealtimeService;
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
    private final ChatRealtimeService realtime;
    private final AuthUtils authUtils;

    public ChatController(ChatService chatService, ChatRealtimeService realtime, AuthUtils authUtils) {
        this.chatService = chatService;
        this.realtime = realtime;
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
        return ResponseEntity.ok(chatService.createGroup(
                me(ud), body.name(), body.memberIds(), body.access(), body.imageUrl()));
    }

    @PostMapping("/direct/{userId}")
    public ResponseEntity<ChatConversationDTO> startDirect(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long userId) {
        return ResponseEntity.ok(chatService.startDirect(me(ud), userId));
    }

    @PatchMapping("/conversations/{id}")
    public ResponseEntity<ChatConversationDTO> updateGroup(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody UpdateGroupDTO body) {
        return ResponseEntity.ok(chatService.updateGroup(me(ud), id, body));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        chatService.deleteConversation(me(ud), id);
        return ResponseEntity.noContent().build();
    }

    // ── Link de convite ────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/invite")
    public ResponseEntity<Map<String, String>> getInvite(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(tokenBody(chatService.getInviteToken(me(ud), id)));
    }

    @PostMapping("/conversations/{id}/invite")
    public ResponseEntity<Map<String, String>> rotateInvite(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(tokenBody(chatService.rotateInviteToken(me(ud), id)));
    }

    @DeleteMapping("/conversations/{id}/invite")
    public ResponseEntity<Void> revokeInvite(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        chatService.revokeInviteToken(me(ud), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite/{token}")
    public ResponseEntity<ChatConversationDTO> acceptInvite(
            @AuthenticationPrincipal UserDetails ud, @PathVariable String token) {
        return ResponseEntity.ok(chatService.acceptInvite(me(ud), token));
    }

    private static Map<String, String> tokenBody(String token) {
        return token == null ? Map.of() : Map.of("token", token);
    }

    // ── Diretório de grupos ────────────────────────────────────────────

    @GetMapping("/directory")
    public ResponseEntity<List<GroupDirectoryEntryDTO>> directory(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(chatService.directory(me(ud), q, page));
    }

    @PostMapping("/conversations/{id}/join")
    public ResponseEntity<Map<String, String>> join(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("state", chatService.joinOrRequest(me(ud), id)));
    }

    @DeleteMapping("/conversations/{id}/join")
    public ResponseEntity<Void> cancelJoin(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        chatService.cancelJoinRequest(me(ud), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/requests")
    public ResponseEntity<List<JoinRequestDTO>> requests(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(chatService.listJoinRequests(me(ud), id));
    }

    @PostMapping("/conversations/{id}/requests/{userId}/approve")
    public ResponseEntity<Void> approve(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id, @PathVariable Long userId) {
        chatService.resolveJoinRequest(me(ud), id, userId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/requests/{userId}/reject")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id, @PathVariable Long userId) {
        chatService.resolveJoinRequest(me(ud), id, userId, false);
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
        Long uid = me(ud);
        ChatMessageDTO dto = chatService.send(uid, id, body.body(), body.replyToId());
        realtime.broadcast(id, "created", uid, dto);
        realtime.touch(id);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/messages/{messageId}")
    public ResponseEntity<ChatMessageDTO> editMessage(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long messageId,
            @RequestBody EditMessageDTO body) {
        Long uid = me(ud);
        ChatMessageDTO dto = chatService.editMessage(uid, messageId, body.body());
        realtime.broadcast(dto.conversationId(), "edited", uid, dto);
        realtime.touch(dto.conversationId());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<ChatMessageDTO> deleteMessage(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long messageId) {
        Long uid = me(ud);
        ChatMessageDTO dto = chatService.deleteMessage(uid, messageId);
        realtime.broadcast(dto.conversationId(), "deleted", uid, dto);
        realtime.touch(dto.conversationId());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/messages/{messageId}/react")
    public ResponseEntity<ChatMessageDTO> react(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long messageId,
            @RequestBody ReactDTO body) {
        Long uid = me(ud);
        ChatMessageDTO dto = chatService.react(uid, messageId, body.emoji());
        realtime.broadcast(dto.conversationId(), "reacted", uid, dto);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        chatService.markRead(me(ud), id);
        return ResponseEntity.noContent().build();
    }

    // ── Membros / cargos ───────────────────────────────────────────────

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

    @PutMapping("/conversations/{id}/members/{userId}/role")
    public ResponseEntity<List<ConversationMemberDTO>> setRole(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestBody SetRoleDTO body) {
        return ResponseEntity.ok(chatService.setRole(me(ud), id, userId, body.role()));
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
