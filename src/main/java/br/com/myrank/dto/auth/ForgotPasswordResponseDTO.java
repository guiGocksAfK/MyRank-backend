package br.com.myrank.dto.auth;

/**
 * Resposta do "esqueci minha senha".
 * status SENT: email mandado (ou email sem conta — resposta igual de propósito).
 * status SOCIAL: a conta não tem senha; {@code provider} diz se entra por GOOGLE ou DISCORD.
 */
public record ForgotPasswordResponseDTO(String status, String provider) {

    public static ForgotPasswordResponseDTO sent() {
        return new ForgotPasswordResponseDTO("SENT", null);
    }

    public static ForgotPasswordResponseDTO social(String provider) {
        return new ForgotPasswordResponseDTO("SOCIAL", provider);
    }
}
