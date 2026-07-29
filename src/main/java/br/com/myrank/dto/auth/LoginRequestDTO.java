package br.com.myrank.dto.auth;

public record LoginRequestDTO(
        String username,
        String password
) {}