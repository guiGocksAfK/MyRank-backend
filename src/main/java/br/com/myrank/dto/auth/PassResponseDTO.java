package br.com.myrank.dto.auth;

/**
 * Passe que prova a posse do email depois do código; libera a etapa seguinte
 * (criar a conta ou trocar a senha).
 */
public record PassResponseDTO(String pass) {}
