package br.com.myrank.dto;

public record UserCreateDTO(
        String name,
        String username,
        String email,
        String password
) {}