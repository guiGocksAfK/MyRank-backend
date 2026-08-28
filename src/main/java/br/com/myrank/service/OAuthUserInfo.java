package br.com.myrank.service;

public record OAuthUserInfo(
        String providerId,
        String email,
        String usernameBase,
        String avatarUrl
) {}
