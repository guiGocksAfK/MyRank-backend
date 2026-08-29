package br.com.myrank.dto;

public record UserUpdateDTO(
        String username,
        String bio,
        String language
) {}