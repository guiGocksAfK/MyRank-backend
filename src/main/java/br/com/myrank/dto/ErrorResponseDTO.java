package br.com.myrank.dto;

import java.time.LocalDateTime;

/**
 * Corpo de resposta padrão para erros da API — usado pelos exception handlers.
 * Mesmo formato em toda a aplicação, para o frontend tratar de forma consistente
 * (ex.: err?.response?.data?.message, que o front já usa em vários lugares).
 */
public class ErrorResponseDTO {

    private int status;
    private String message;
    private LocalDateTime timestamp;

    public ErrorResponseDTO() {}

    public ErrorResponseDTO(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}