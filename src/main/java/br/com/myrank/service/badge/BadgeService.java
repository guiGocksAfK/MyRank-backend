package br.com.myrank.service.badge;

import br.com.myrank.domain.entity.Badge;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.UserBadge;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.BadgeResponseDTO;
import br.com.myrank.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
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
    private final MasterTableGroupRepository masterTableGroupRepository;
    private final UserRepository userRepository;
    private final UserAvatarRepository userAvatarRepository;

    public BadgeService(BadgeRepository badgeRepository,
                        UserBadgeRepository userBadgeRepository,
                        WorkRepository workRepository,
                        CategoryRepository categoryRepository,
                        MasterTableGroupRepository masterTableGroupRepository,
                        UserRepository userRepository,
                        UserAvatarRepository userAvatarRepository) {
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.workRepository = workRepository;
        this.categoryRepository = categoryRepository;
        this.masterTableGroupRepository = masterTableGroupRepository;
        this.userRepository = userRepository;
        this.userAvatarRepository = userAvatarRepository;
    }

    /** Recalcula todo o progresso de badges do usuário. Nunca lança pra fora. */
    @Transactional
    public void recalculate(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;

            BadgeContext ctx = buildContext(user);

            Map<String, Badge> catalog = new HashMap<>();
            for (Badge b : badgeRepository.findAll()) catalog.put(b.getCode(), b);

            Map<Long, UserBadge> existing = new HashMap<>();
            for (UserBadge ub : userBadgeRepository.findByUserId(userId)) {
                existing.put(ub.getBadgeId(), ub);
            }

            LocalDateTime now = LocalDateTime.now();

            for (BadgeDefinition def : BadgeDefinition.values()) {
                Badge badge = catalog.get(def.code());
                if (badge == null) continue; // catálogo ainda não sincronizado

                int raw = def.rawProgress(ctx);
                int clamped = Math.min(raw, def.target());
                boolean reached = raw >= def.target();

                UserBadge ub = existing.get(badge.getId());
                if (ub == null) ub = new UserBadge(userId, badge.getId());

                ub.setCurrentProgress(clamped);
                // uma vez conquistada, continua conquistada
                if (reached && ub.getUnlockedAt() == null) {
                    ub.setUnlockedAt(now);
                }
                userBadgeRepository.save(ub);
            }
        } catch (Exception e) {
            log.warn("Falha ao recalcular badges do usuário {}: {}", userId, e.getMessage());
        }
    }

    /** Recalcula e devolve o catálogo com o progresso do usuário. */
    @Transactional
    public List<BadgeResponseDTO> listForUser(Long userId) {
        recalculate(userId);

        Map<Long, UserBadge> progress = new HashMap<>();
        for (UserBadge ub : userBadgeRepository.findByUserId(userId)) {
            progress.put(ub.getBadgeId(), ub);
        }

        return badgeRepository.findAllByOrderBySortOrderAsc().stream().map(badge -> {
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
            String type = inferType(w.getCategory() != null ? w.getCategory().getName() : null);
            double score = w.getScore() != null ? w.getScore().doubleValue() : 0.0;
            String creatorKey = normalizeCreator(w.getCreator());
            Integer year = w.getReleaseDate() != null ? w.getReleaseDate().getYear() : null;
            LocalDate added = w.getCreatedAt() != null ? w.getCreatedAt().toLocalDate() : null;
            boolean edited = w.getCreatedAt() != null && w.getUpdatedAt() != null
                    && ChronoUnit.SECONDS.between(w.getCreatedAt(), w.getUpdatedAt()) > 60;
            return new BadgeContext.WorkView(type, score, w.getTimeMinutes(), creatorKey, year, added, edited);
        }).toList();

        boolean hasAvatar = (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank())
                || userAvatarRepository.existsById(userId);
        boolean hasBio = user.getBio() != null && !user.getBio().isBlank();
        boolean hasCustomCategory = categoryRepository.findByUserId(userId).stream()
                .anyMatch(c -> !c.isDefault());
        boolean hasMasterGroup = !masterTableGroupRepository.findByUserId(userId).isEmpty();
        long accountAgeDays = user.getCreatedAt() != null
                ? ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now())
                : 0L;

        return new BadgeContext(views, hasAvatar, hasBio, hasCustomCategory, hasMasterGroup, accountAgeDays);
    }

    private static String normalizeCreator(String creator) {
        if (creator == null) return null;
        String t = creator.trim().toLowerCase();
        return t.isEmpty() ? null : t;
    }

    /** Mesma lógica do front (useUnifiedItems): infere o tipo pelo nome da categoria. */
    static String inferType(String categoryName) {
        if (categoryName == null) return "outro";
        String s = Normalizer.normalize(categoryName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        if (s.matches(".*(livro|book).*")) return "livro";
        if (s.matches(".*(jogo|game).*")) return "jogo";
        if (s.contains("anime")) return "anime";
        if (s.matches(".*(serie|series|show|\\btv\\b).*")) return "serie";
        if (s.matches(".*(filme|movie).*")) return "filme";
        return "outro";
    }
}
