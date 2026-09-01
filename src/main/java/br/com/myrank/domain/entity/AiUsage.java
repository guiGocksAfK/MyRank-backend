package br.com.myrank.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Orçamento diário de mensagens de IA de um usuário. Uma linha por usuário.
 * {@code windowStart} é a fronteira das 06:00 da janela vigente; quando o serviço
 * observa uma janela defasada, zera {@code used}. Ver {@code AiUsageService}.
 */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(nullable = false)
    private int used;

    public AiUsage() {}

    public AiUsage(Long userId, LocalDateTime windowStart, int used) {
        this.userId = userId;
        this.windowStart = windowStart;
        this.used = used;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }

    public int getUsed() { return used; }
    public void setUsed(int used) { this.used = used; }
}
