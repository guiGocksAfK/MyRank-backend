package br.com.myrank.service.social;

import br.com.myrank.domain.entity.FeedEvent;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.FeedEventType;
import br.com.myrank.repository.FeedEventRepository;
import br.com.myrank.repository.WorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Na primeira subida com a feature de social (tabela feed_events vazia),
 * cria um evento ADDED por obra já cadastrada, usando a data de criação
 * da obra — assim o feed já nasce com histórico.
 */
@Component
@Order(2)
public class SocialBackfillInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SocialBackfillInitializer.class);

    private final FeedEventRepository feedEventRepository;
    private final WorkRepository workRepository;

    public SocialBackfillInitializer(FeedEventRepository feedEventRepository,
                                     WorkRepository workRepository) {
        this.feedEventRepository = feedEventRepository;
        this.workRepository = workRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (feedEventRepository.count() > 0) return;

        List<Work> works = workRepository.findAll();
        if (works.isEmpty()) return;

        works.stream()
                .sorted(Comparator.comparing(Work::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(w -> {
                    FeedEvent e = new FeedEvent();
                    e.setUserId(w.getUser().getId());
                    e.setType(FeedEventType.ADDED);
                    e.setWorkId(w.getId());
                    e.setScore(w.getScore());
                    e.setCreatedAt(w.getCreatedAt());
                    feedEventRepository.save(e);
                });

        log.info("Social backfill: {} eventos ADDED criados a partir das obras existentes.", works.size());
    }
}
