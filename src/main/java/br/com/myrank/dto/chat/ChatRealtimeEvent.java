package br.com.myrank.dto.chat;

/**
 * Evento entregue aos membros atuais pelo canal privado /user/queue/chat-events.
 * type: created | edited | deleted | reacted.
 * Os flags "mine" da mensagem vêm zerados — cada cliente recalcula pelo senderId.
 */
public record ChatRealtimeEvent(
        String type,
        Long conversationId,
        Long actorId,        // quem disparou (o cliente ignora o eco da própria ação)
        ChatMessageDTO message
) {}
