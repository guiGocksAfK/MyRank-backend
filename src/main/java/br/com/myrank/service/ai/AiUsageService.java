package br.com.myrank.service.ai;

import br.com.myrank.repository.AiUsageRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Orçamento diário de mensagens de IA por usuário. Gerar uma análise nova e cada
 * pergunta do chat contam 1. O contador zera todo dia às {@link #RESET_HOUR}h.
 */
@Service
public class AiUsageService {

    /** Mensagens de IA por usuário por janela diária. */
    public static final int DAILY_LIMIT = 15;
    /** Hora local em que o orçamento reseta. */
    private static final int RESET_HOUR = 6;

    private final AiUsageRepository repository;
    private final NamedParameterJdbcTemplate jdbc;

    public AiUsageService(AiUsageRepository repository, NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    public record Reservation(LocalDateTime windowStart, int remaining) {}

    /** Início da janela vigente: as {@link #RESET_HOUR}h de hoje, ou de ontem se ainda não deu essa hora. */
    private LocalDateTime currentWindowStart() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate day = now.toLocalTime().isBefore(LocalTime.of(RESET_HOUR, 0))
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
        return day.atTime(RESET_HOUR, 0);
    }

    /** Quantas mensagens ainda cabem na janela atual (não consome). */
    @Transactional(readOnly = true)
    public int remaining(Long userId) {
        LocalDateTime window = currentWindowStart();
        return repository.findById(userId)
                .filter(u -> !u.getWindowStart().isBefore(window))
                .map(u -> Math.max(0, DAILY_LIMIT - u.getUsed()))
                .orElse(DAILY_LIMIT);
    }

    /**
     * Reserva uma chamada antes do pedido ao provedor. O upsert do PostgreSQL é
     * atômico mesmo quando várias requisições chegam ao mesmo tempo ou em instâncias
     * diferentes do backend. A transação precisa terminar antes da chamada externa.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(Long userId) {
        LocalDateTime window = currentWindowStart();
        String sql = """
                INSERT INTO ai_usage (user_id, window_start, used)
                VALUES (:userId, :windowStart, 1)
                ON CONFLICT (user_id) DO UPDATE SET
                    window_start = CASE WHEN ai_usage.window_start < EXCLUDED.window_start
                                        THEN EXCLUDED.window_start ELSE ai_usage.window_start END,
                    used = CASE WHEN ai_usage.window_start < EXCLUDED.window_start
                                THEN 1 ELSE ai_usage.used + 1 END
                WHERE ai_usage.window_start < EXCLUDED.window_start OR ai_usage.used < :limit
                RETURNING used
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("windowStart", Timestamp.valueOf(window))
                .addValue("limit", DAILY_LIMIT);
        var used = jdbc.query(sql, params, (rs, rowNum) -> rs.getInt("used"));
        if (used.isEmpty()) {
            throw new IllegalArgumentException(limitMessage());
        }
        return new Reservation(window, DAILY_LIMIT - used.get(0));
    }

    /** Uma falha do provedor não consome a cota; não mexe numa janela já renovada. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long userId, Reservation reservation) {
        jdbc.update("""
                UPDATE ai_usage SET used = used - 1
                WHERE user_id = :userId AND window_start = :windowStart AND used > 0
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("windowStart", Timestamp.valueOf(reservation.windowStart())));
    }

    private String limitMessage() {
        return "Você usou suas " + DAILY_LIMIT + " mensagens de IA de hoje. O limite zera às "
                + RESET_HOUR + "h.";
    }
}
