package br.com.myrank.dto;

import java.time.LocalDateTime;

/**
 * `title` e `message` já vêm prontos em pt-BR (a frase mora no backend).
 * Os campos estruturados ficam pra quem quiser navegar/estilizar.
 */
public record NotificationDTO(
        Long id,
        String type,            // REACTION | FOLLOW | TAKE
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ActorDTO actor,
        int actorCount,
        String reactionKind,    // up | agree | disagree  (só REACTION)
        WorkMiniDTO work,       // contexto (post/take é sobre essa obra)
        Long feedEventId,
        String title,
        String message
) {}
