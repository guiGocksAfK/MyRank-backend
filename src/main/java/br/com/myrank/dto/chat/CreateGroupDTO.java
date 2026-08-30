package br.com.myrank.dto.chat;

import java.util.List;

public record CreateGroupDTO(
        String name,
        List<Long> memberIds
) {}
