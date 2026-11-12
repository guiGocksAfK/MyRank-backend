package br.com.myrank.service.badge;

import br.com.myrank.domain.entity.Badge;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.UserBadge;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.BadgeResponseDTO;
import br.com.myrank.repository.*;
import br.com.myrank.service.CreatorNames;
import br.com.myrank.service.social.FeedEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BadgeService {

    private static final Logger log = LoggerFactory.getLogger(BadgeService.class);

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final WorkRepository workRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final FeedEventService feedEventService;
    private final Clock clock;

    /**
     * Catálogo de badges ordenado por sortOrder — a tabela `badges` só muda no boot
     * (`BadgeCatalogInitializer`), então manter em memória evita consultar o banco
     * em todo recálculo e listagem.
     */
    private volatile List<Badge> cachedCatalog;

    @Autowired
    public BadgeService(BadgeRepository badgeRepository,
                        UserBadgeRepository userBadgeRepository,
                        WorkRepository workRepository,
                        CategoryRepository categoryRepository,
                        UserRepository userRepository,
                        FeedEventService feedEventService,
                        Clock clock) {
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.workRepository = workRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.feedEventService = feedEventService;
        this.clock = clock;
    }

    /** Invalida o cache do catálogo (chamado pelo `BadgeCatalogInitializer` após sincronizar o boot). */
    public void invalidateCatalogCache() {
        this.cachedCatalog = null;
    }

    private List<Badge> getCatalog() {
        List<Badge> local = this.cachedCatalog;
        if (local == null || local.isEmpty()) {
            local = List.copyOf(badgeRepository.findAllByOrderBySortOrderAsc());
            if (!local.isEmpty()) {
                this.cachedCatalog = local;
            }
        }
        return local;
    }

    /**
     * Recálculo em segundo plano — chamado pelos CRUDs de obra pra não segurar a
     * resposta da escrita enquanto reprocessa as 72 badges. Roda no `badgeExecutor`,
     * em transação própria (o CRUD que disparou já commitou).
     */
    @Async("badgeExecutor")
    @Transactional
    public void recalculateAsync(Long userId) {
        recalculate(userId);
    }

    /** Recalcula todo o progresso de badges do usuário. Nunca lança pra fora. */
    @Transactional
    public void recalculate(Long userId) {
        recalculateAndGetProgress(userId, getCatalog());
    }

    private Map<Long, UserBadge> recalculateAndGetProgress(Long userId, List<Badge> catalogList) {
        Map<Long, UserBadge> existing = new HashMap<>();
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return existing;

            BadgeContext ctx = buildContext(user);

            Map<String, Badge> catalog = new HashMap<>(catalogList.size() * 2);
            for (Badge b : catalogList) catalog.put(b.getCode(), b);

            for (UserBadge ub : userBadgeRepository.findByUserId(userId)) {
                existing.put(ub.getBadgeId(), ub);
            }

            LocalDateTime now = LocalDateTime.now(clock);
            List<Long> justUnlocked = new java.util.ArrayList<>();
            List<UserBadge> toSave = new java.util.ArrayList<>();

            for (BadgeDefinition def : BadgeDefinition.values()) {
                Badge badge = catalog.get(def.code());
                if (badge == null) continue; // catálogo ainda não sincronizado

                int raw = def.rawProgress(ctx);
                int clamped = Math.min(raw, def.target());
                boolean reached = raw >= def.target();

                UserBadge ub = existing.get(badge.getId());
                boolean isNew = ub == null;
                if (isNew) {
                    ub = new UserBadge(userId, badge.getId());
                    existing.put(badge.getId(), ub);
                }

                boolean changed = isNew || ub.getCurrentProgress() != clamped;
                ub.setCurrentProgress(clamped);
                // uma vez conquistada, continua conquistada
                if (reached && ub.getUnlockedAt() == null) {
                    ub.setUnlockedAt(now);
                    justUnlocked.add(badge.getId());
                    changed = true;
                }
                if (changed) {
                    toSave.add(ub);
                }
            }

            if (!toSave.isEmpty()) {
                userBadgeRepository.saveAll(toSave);
            }

            // feed: um evento por badge recém-conquistada
            justUnlocked.forEach(badgeId -> feedEventService.recordBadgeUnlocked(userId, badgeId));
        } catch (Exception e) {
            log.warn("Falha ao recalcular badges do usuário {}: {}", userId, e.getMessage());
        }
        return existing;
    }

    /** Recalcula e devolve o catálogo com o progresso do usuário. */
    @Transactional
    public List<BadgeResponseDTO> listForUser(Long userId) {
        List<Badge> catalogList = getCatalog();
        Map<Long, UserBadge> progress = recalculateAndGetProgress(userId, catalogList);

        return catalogList.stream().map(badge -> {
            UserBadge ub = progress.get(badge.getId());
            int prog = ub != null ? ub.getCurrentProgress() : 0;
            LocalDateTime unlockedAt = ub != null ? ub.getUnlockedAt() : null;
            return new BadgeResponseDTO(
                    badge.getCode(),
                    badge.getBucket(),
                    badge.getName(),
                    badge.getDescription(),
                    badge.getIcon(),
                    badge.getTargetProgress(),
                    badge.isHasProgress(),
                    prog,
                    unlockedAt != null,
                    unlockedAt
            );
        }).toList();
    }

    // ── montagem do contexto ─────────────────────────────────────────

    private BadgeContext buildContext(User user) {
        Long userId = user.getId();
        List<Work> works = workRepository.findByUserId(userId);

        List<BadgeContext.WorkView> views = works.stream().map(w -> {
            String type = w.getTemplate().type();
            double score = w.getScore() != null ? w.getScore().doubleValue() : 0.0;
            List<String> creatorKeys = CreatorNames.keys(w.getCreator());
            Integer year = w.getReleaseDate() != null ? w.getReleaseDate().getYear() : null;
            LocalDate added = w.getCreatedAt() != null ? w.getCreatedAt().toLocalDate() : null;
            boolean edited = w.getCreatedAt() != null && w.getUpdatedAt() != null
                    && ChronoUnit.SECONDS.between(w.getCreatedAt(), w.getUpdatedAt()) > 60;
            Long tableId = w.getCategory() != null ? w.getCategory().getId() : null;
            return new BadgeContext.WorkView(type, score, w.getTimeMinutes(), creatorKeys, year, added, edited, tableId);
        }).toList();

        boolean hasAvatar = user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank();
        boolean hasBio = user.getBio() != null && !user.getBio().isBlank();
        boolean hasCustomCategory = categoryRepository.findByUserId(userId).stream()
                .anyMatch(c -> !c.isDefault());
        long accountAgeDays = user.getCreatedAt() != null
                ? ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now(clock))
                : 0L;

        return new BadgeContext(views, hasAvatar, hasBio, hasCustomCategory, accountAgeDays);
    }

}
