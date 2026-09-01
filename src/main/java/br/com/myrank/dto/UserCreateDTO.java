package br.com.myrank.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateDTO(
        @NotBlank(message = "Informe um nome de usuário.")
        @Size(min = 3, max = 50, message = "O nome de usuário deve ter de 3 a 50 caracteres.")
        String username,

        @NotBlank(message = "Informe seu email.")
        @Email(message = "Email inválido.")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Informe uma senha.")
        @Size(min = 8, max = 100, message = "A senha deve ter pelo menos 8 caracteres.")
        String password
) {}
