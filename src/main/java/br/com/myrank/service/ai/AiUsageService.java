package br.com.myrank.service.ai;

import br.com.myrank.domain.entity.AiUsage;
import br.com.myrank.repository.AiUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AiUsageService(AiUsageRepository repository) {
        this.repository = repository;
    }

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

    /** Barra a operação (→ 400) se o usuário já bateu o limite do dia. Não consome. */
    @Transactional(readOnly = true)
    public void ensureWithinLimit(Long userId) {
        if (remaining(userId) <= 0) {
            throw new IllegalArgumentException(limitMessage());
        }
    }

    /**
     * Consome uma mensagem do orçamento. Lança {@link IllegalArgumentException}
     * (→ 400) se o usuário já bateu o limite do dia. Devolve o que sobrou.
     */
    @Transactional
    public int consume(Long userId) {
        LocalDateTime window = currentWindowStart();
        AiUsage usage = repository.findById(userId)
                .orElseGet(() -> new AiUsage(userId, window, 0));

        if (usage.getWindowStart().isBefore(window)) { // janela virou → zera
            usage.setWindowStart(window);
            usage.setUsed(0);
        }

        if (usage.getUsed() >= DAILY_LIMIT) {
            throw new IllegalArgumentException(limitMessage());
        }

        usage.setUsed(usage.getUsed() + 1);
        repository.save(usage);
        return DAILY_LIMIT - usage.getUsed();
    }

    private String limitMessage() {
        return "Você usou suas " + DAILY_LIMIT + " mensagens de IA de hoje. O limite zera às "
                + RESET_HOUR + "h.";
    }
}
