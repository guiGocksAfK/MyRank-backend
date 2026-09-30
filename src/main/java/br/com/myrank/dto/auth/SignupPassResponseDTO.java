package br.com.myrank.dto.auth;

/** Passe que prova a posse do email; o cadastro final (POST /api/users) exige. */
public record SignupPassResponseDTO(String signupPass) {}
