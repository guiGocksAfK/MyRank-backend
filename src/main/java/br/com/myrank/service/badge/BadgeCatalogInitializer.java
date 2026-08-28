package br.com.myrank.service.badge;

import br.com.myrank.domain.entity.Badge;
import br.com.myrank.repository.BadgeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sincroniza a tabela {@code badges} com o enum {@link BadgeDefinition} a cada
 * startup (upsert por {@code code}). Assim dá pra iterar em nome/descrição/meta
 * das badges sem precisar dropar o banco.
 */
@Component
@Order(1)
public class BadgeCatalogInitializer implements ApplicationRunner {

    private final BadgeRepository badgeRepository;

    public BadgeCatalogInitializer(BadgeRepository badgeRepository) {
        this.badgeRepository = badgeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (BadgeDefinition def : BadgeDefinition.values()) {
            Badge badge = badgeRepository.findByCode(def.code()).orElseGet(Badge::new);
            badge.setCode(def.code());
            badge.setBucket(def.bucket());
            badge.setName(def.displayName());
            badge.setDescription(def.description());
            badge.setIcon(def.icon());
            badge.setTargetProgress(def.target());
            badge.setHasProgress(def.hasProgress());
            badge.setSortOrder(def.ordinal());
            badgeRepository.save(badge);
        }

        // Remove badges que saíram do enum (o FK user_badges → badges é ON DELETE CASCADE).
        Set<String> known = Arrays.stream(BadgeDefinition.values())
                .map(BadgeDefinition::code)
                .collect(Collectors.toSet());
        badgeRepository.findAll().stream()
                .filter(b -> !known.contains(b.getCode()))
                .forEach(badgeRepository::delete);
    }
}
