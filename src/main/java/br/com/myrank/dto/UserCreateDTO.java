package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro com senha. O email vem do passe, emitido depois de confirmar o código. */
public record UserCreateDTO(
        @NotBlank(message = "Confirme seu email antes de criar a conta.")
        @Size(max = 1000)
        String signupPass,

        @NotBlank(message = "Informe um nome de usuário.")
        @Size(min = 3, max = 50, message = "O nome de usuário deve ter de 3 a 50 caracteres.")
        String username,

        @NotBlank(message = "Informe uma senha.")
        @Size(min = 8, max = 100, message = "A senha deve ter pelo menos 8 caracteres.")
        String password,

        /** Opcional (PT | EN | ES): idioma da conta. */
        @Size(max = 5)
        String language
) {}
