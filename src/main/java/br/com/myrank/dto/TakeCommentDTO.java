package br.com.myrank.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Comentário de um take (com suas respostas, quando é raiz). */
public record TakeCommentDTO(
        Long id,
        Long takeId,
        Long parentId,
        ActorDTO author,
        String text,
        LocalDateTime createdAt,
        boolean canDelete,
        List<TakeCommentDTO> replies
) {}
