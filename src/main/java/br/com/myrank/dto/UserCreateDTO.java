package br.com.myrank.dto;

public record UserCreateDTO(
        String username,
        String email,
        String password
) {}