package br.com.myrank.service.social;

import br.com.myrank.dto.chat.ChatMessageDTO;
import br.com.myrank.dto.chat.ChatRealtimeEvent;
import br.com.myrank.dto.chat.ReactionCountDTO;
import br.com.myrank.repository.ConversationMemberRepository;
import br.com.myrank.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Empurra eventos de chat pelos canais STOMP. Best-effort: nunca lança pra fora
 * (o REST + polling continuam sendo a fonte de verdade / fallback).
 */
@Service
public class ChatRealtimeService {

    private static final Logger log = LoggerFactory.getLogger(ChatRealtimeService.class);

    private final SimpMessagingTemplate messaging;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;

    public ChatRealtimeService(SimpMessagingTemplate messaging,
                               ConversationMemberRepository memberRepository,
                               UserRepository userRepository) {
        this.messaging = messaging;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /** Evento numa conversa aberta (created | edited | deleted | reacted). */
    public void broadcast(Long convId, String type, Long actorId, ChatMessageDTO dto) {
        if (convId == null) return;
        try {
            messaging.convertAndSend("/topic/conversation." + convId,
                    new ChatRealtimeEvent(type, convId, actorId, sanitize(dto)));
        } catch (Exception e) {
            log.warn("broadcast({}, {}) falhou: {}", convId, type, e.getMessage());
        }
    }

    /** Ping leve pra sidebar/badge de não-lidas de cada membro. */
    public void touch(Long convId) {
        if (convId == null) return;
        try {
            List<Long> memberIds = memberRepository.findMemberIds(convId);
            if (memberIds.isEmpty()) return;
            userRepository.findAllById(memberIds).forEach(u -> {
                if (u.getEmail() != null) {
                    messaging.convertAndSendToUser(u.getEmail(), "/queue/chat",
                            Map.of("conversationId", convId));
                }
            });
        } catch (Exception e) {
            log.warn("touch({}) falhou: {}", convId, e.getMessage());
        }
    }

    /** Zera os flags "mine" — cada cliente recalcula pelo próprio id. */
    private static ChatMessageDTO sanitize(ChatMessageDTO m) {
        List<ReactionCountDTO> rx = m.reactions() == null ? List.of()
                : m.reactions().stream()
                        .map(r -> new ReactionCountDTO(r.emoji(), r.count(), false))
                        .toList();
        return new ChatMessageDTO(
                m.id(), m.conversationId(), m.senderId(), m.senderName(), m.senderAvatarUrl(),
                false, m.kind(), m.body(), m.edited(), m.deleted(), m.replyTo(), rx, m.createdAt());
    }
}
