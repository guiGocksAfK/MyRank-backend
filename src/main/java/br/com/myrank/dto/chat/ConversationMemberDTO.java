package br.com.myrank.dto.chat;

public record ConversationMemberDTO(
        Long userId,
        String username,
        String avatarUrl,
        String role           // OWNER | MEMBER
) {}
