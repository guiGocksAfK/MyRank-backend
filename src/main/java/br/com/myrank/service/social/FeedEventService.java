package br.com.myrank.service.social;

import br.com.myrank.domain.entity.FeedEvent;
import br.com.myrank.domain.entity.Take;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.FeedEventType;
import br.com.myrank.repository.FeedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava linhas em `feed_events`. Chamado por WorkService, BadgeService e
 * SocialService. Nunca propaga exceção — uma falha aqui não pode quebrar
 * o fluxo principal (salvar obra, etc.).
 */
@Service
public class FeedEventService {

    private static final Logger log = LoggerFactory.getLogger(FeedEventService.class);

    private final FeedEventRepository feedEventRepository;

    public FeedEventService(FeedEventRepository feedEventRepository) {
        this.feedEventRepository = feedEventRepository;
    }

    @Transactional
    public void recordAdded(Work work) {
        safeSave(() -> {
            FeedEvent e = base(work.getUser().getId(), FeedEventType.ADDED);
            e.setWorkId(work.getId());
            e.setScore(work.getScore());
            return e;
        });
    }

    @Transactional
    public void recordRated(Work work) {
        safeSave(() -> {
            FeedEvent e = base(work.getUser().getId(), FeedEventType.RATED);
            e.setWorkId(work.getId());
            e.setScore(work.getScore());
            return e;
        });
    }

    @Transactional
    public void recordBadgeUnlocked(Long userId, Long badgeId) {
        safeSave(() -> {
            FeedEvent e = base(userId, FeedEventType.BADGE);
            e.setBadgeId(badgeId);
            return e;
        });
    }

    @Transactional
    public FeedEvent recordTake(Take take, Work work) {
        FeedEvent e = base(take.getUserId(), FeedEventType.TAKE);
        e.setWorkId(take.getWorkId());
        e.setTakeId(take.getId());
        e.setScore(work != null ? work.getScore() : null);
        return feedEventRepository.save(e);
    }

    // ── infra ──────────────────────────────────────────────

    private static FeedEvent base(Long userId, FeedEventType type) {
        FeedEvent e = new FeedEvent();
        e.setUserId(userId);
        e.setType(type);
        return e;
    }

    private void safeSave(java.util.function.Supplier<FeedEvent> build) {
        try {
            feedEventRepository.save(build.get());
        } catch (Exception ex) {
            log.warn("Falha ao gravar feed_event: {}", ex.getMessage());
        }
    }
}
