package br.com.myrank.dto.chat;

import jakarta.validation.constraints.Size;

import java.util.List;

public record AddMembersDTO(
        @Size(max = 50, message = "Máximo de 50 participantes.")
        List<Long> userIds
) {}
