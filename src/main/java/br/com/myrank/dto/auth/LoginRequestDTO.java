package br.com.myrank.dto.auth;

public record LoginRequestDTO(
        String email,
        String password
) {}