package br.com.myrank.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank(message = "Link de redefinição inválido.")
        @Size(max = 100, message = "Link de redefinição inválido.")
        String token,

        @NotBlank(message = "Informe uma senha.")
        @Size(min = 8, max = 100, message = "A senha deve ter pelo menos 8 caracteres.")
        String password
) {}
