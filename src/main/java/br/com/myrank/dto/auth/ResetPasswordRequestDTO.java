package br.com.myrank.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank(message = "Confirme o código antes de trocar a senha.")
        @Size(max = 1000)
        String resetPass,

        @NotBlank(message = "Informe uma senha.")
        @Size(min = 8, max = 100, message = "A senha deve ter pelo menos 8 caracteres.")
        String password
) {}
