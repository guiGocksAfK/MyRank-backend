package br.com.myrank.dto.auth;

public record LoginResponseDTO(
        String token,
        String username
) {}