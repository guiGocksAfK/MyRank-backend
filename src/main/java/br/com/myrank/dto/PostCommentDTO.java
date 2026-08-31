package br.com.myrank.dto;

/** Corpo de POST /social/takes/{id}/comments. `parentCommentId` null = comentário raiz. */
public record PostCommentDTO(String text, Long parentCommentId) {}
