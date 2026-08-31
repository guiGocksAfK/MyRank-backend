package br.com.myrank.service.social;

import br.com.myrank.domain.entity.*;
import br.com.myrank.domain.enums.FeedEventType;
import br.com.myrank.domain.enums.ReactionKind;
import br.com.myrank.dto.*;
import br.com.myrank.repository.*;
import br.com.myrank.service.WorkTypeResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SocialService {

    private static final int MAX_LIST = 20;
    private static final int TAKE_MIN = 3;
    private static final int TAKE_MAX = 280;

    private final FollowRepository followRepository;
    private final FollowRequestRepository followRequestRepository;
    private final FeedEventRepository feedEventRepository;
    private final FeedReactionRepository feedReactionRepository;
    private final TakeRepository takeRepository;
    private final WorkRepository workRepository;
    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BadgeRepository badgeRepository;
    private final FeedEventService feedEventService;
    private final NotificationService notificationService;

    public SocialService(FollowRepository followRepository,
                         FollowRequestRepository followRequestRepository,
                         FeedEventRepository feedEventRepository,
                         FeedReactionRepository feedReactionRepository,
                         TakeRepository takeRepository,
                         WorkRepository workRepository,
                         UserRepository userRepository,
                         UserBadgeRepository userBadgeRepository,
                         BadgeRepository badgeRepository,
                         FeedEventService feedEventService,
                         NotificationService notificationService) {
        this.followRepository = followRepository;
        this.followRequestRepository = followRequestRepository;
        this.feedEventRepository = feedEventRepository;
        this.feedReactionRepository = feedReactionRepository;
        this.takeRepository = takeRepository;
        this.workRepository = workRepository;
        this.userRepository = userRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.badgeRepository = badgeRepository;
        this.feedEventService = feedEventService;
        this.notificationService = notificationService;
    }

    // ── Resumo ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SocialSummaryDTO getSummary(Long viewerId) {
        long following = followRepository.countByFollowerId(viewerId);
        long followers = followRepository.countByFollowedId(viewerId);
        long feedCount = feedEventRepository.countFeed(feedActorIds(viewerId), FeedEventType.TAKE);
        long pendingRequests = followRequestRepository.countByTargetId(viewerId);
        return new SocialSummaryDTO(following, followers, feedCount, pendingRequests);
    }

    // ── Feed ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FeedItemDTO> getFeed(Long viewerId, int page, int size) {
        int capped = Math.min(Math.max(size, 1), 50);
        List<FeedEvent> events = feedEventRepository.findFeed(
                feedActorIds(viewerId), FeedEventType.TAKE, PageRequest.of(Math.max(page, 0), capped));
        if (events.isEmpty()) return List.of();

        Map<Long, User> users = byId(userRepository.findAllById(
                events.stream().map(FeedEvent::getUserId).collect(Collectors.toSet())), User::getId);
        Map<Long, Work> works = byId(workRepository.findAllById(
                idsOf(events, FeedEvent::getWorkId)), Work::getId);
        Map<Long, Badge> badges = byId(badgeRepository.findAllById(
                idsOf(events, FeedEvent::getBadgeId)), Badge::getId);
        Map<Long, Take> takes = byId(takeRepository.findAllById(
                idsOf(events, FeedEvent::getTakeId)), Take::getId);

        Map<Long, List<FeedReaction>> reactionsByEvent = feedReactionRepository
                .findByFeedEventIdIn(events.stream().map(FeedEvent::getId).toList())
                .stream().collect(Collectors.groupingBy(FeedReaction::getFeedEventId));

        return events.stream().map(e -> toFeedItem(
                e,
                users.get(e.getUserId()),
                e.getWorkId() != null ? works.get(e.getWorkId()) : null,
                e.getBadgeId() != null ? badges.get(e.getBadgeId()) : null,
                e.getTakeId() != null ? takes.get(e.getTakeId()) : null,
                summarize(reactionsByEvent.getOrDefault(e.getId(), List.of()), viewerId)
        )).toList();
    }

    // ── Listas de usuários ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SocialUserDTO> getFollowing(Long viewerId) {
        return userRepository.findAllById(followRepository.findFollowedIds(viewerId)).stream()
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase()))
                .map(u -> toSocialUser(u, viewerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SocialUserDTO> getFollowers(Long viewerId) {
        return userRepository.findAllById(followRepository.findFollowerIds(viewerId)).stream()
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase()))
                .map(u -> toSocialUser(u, viewerId))
                .toList();
    }

    /** Últimos N seguidores, na ordem em que te seguiram. */
    @Transactional(readOnly = true)
    public List<SocialUserDTO> getRecentFollowers(Long viewerId, int limit) {
        List<Long> ids = followRepository.findRecentFollowerIds(
                viewerId, PageRequest.of(0, Math.max(1, Math.min(limit, 20))));
        if (ids.isEmpty()) return List.of();
        Map<Long, User> byId = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(u -> toSocialUser(u, viewerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SocialUserDTO> getSuggestions(Long viewerId) {
        List<Long> followed = followRepository.findFollowedIds(viewerId);
        List<Long> excluded = followed.isEmpty() ? List.of(-1L) : followed;
        return userRepository
                .findSuggestions(viewerId, excluded, PageRequest.of(0, MAX_LIST)).stream()
                .map(u -> toSocialUser(u, viewerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SocialUserDTO> searchUsers(Long viewerId, String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();
        // perfis privados também aparecem — pra poder pedir pra seguir; o conteúdo
        // continua protegido no getProfile.
        return userRepository.searchByUsername(viewerId, q, PageRequest.of(0, MAX_LIST)).stream()
                .map(u -> toSocialUser(u, viewerId))
                .toList();
    }

    // ── Pedidos de seguir (perfis privados) ────────────────────────────

    @Transactional(readOnly = true)
    public List<SocialUserDTO> getFollowRequests(Long viewerId) {
        List<Long> ids = followRequestRepository.findRequesterIds(viewerId);
        if (ids.isEmpty()) return List.of();
        Map<Long, User> byId = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(u -> toSocialUser(u, viewerId))
                .toList();
    }

    @Transactional
    public void approveFollowRequest(Long viewerId, Long requesterId) {
        FollowRequest req = followRequestRepository
                .findByRequesterIdAndTargetId(requesterId, viewerId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        followRequestRepository.delete(req);
        if (!followRepository.existsByFollowerIdAndFollowedId(requesterId, viewerId)) {
            followRepository.save(new Follow(requesterId, viewerId));
        }
        notificationService.clearFollowRequest(viewerId, requesterId);
        notificationService.onFollowAccepted(requesterId, viewerId);
    }

    @Transactional
    public void rejectFollowRequest(Long viewerId, Long requesterId) {
        followRequestRepository.deleteByRequesterIdAndTargetId(requesterId, viewerId);
        notificationService.clearFollowRequest(viewerId, requesterId);
    }

    // ── Perfil ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SocialProfileDTO getProfile(Long viewerId, Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        boolean self = targetId.equals(viewerId);
        boolean following = !self && followRepository.existsByFollowerIdAndFollowedId(viewerId, targetId);
        boolean followsYou = !self && followRepository.existsByFollowerIdAndFollowedId(targetId, viewerId);
        boolean requested = !self && followRequestRepository.existsByRequesterIdAndTargetId(viewerId, targetId);

        long followerCount = followRepository.countByFollowedId(targetId);
        long followingCount = followRepository.countByFollowerId(targetId);

        // privado e o viewer não segue → só cabeçalho + contagens
        if (!self && !target.isPublic() && !following) {
            return new SocialProfileDTO(
                    target.getId(), target.getUsername(), target.getAvatarUrl(), target.getBio(),
                    target.getPlan().name(), 0, 0.0,
                    false, followsYou, false, requested, true,
                    followerCount, followingCount,
                    List.of(), Map.of(), List.of(), List.of());
        }

        List<Work> works = workRepository.findByUserId(targetId);
        List<WorkMiniDTO> minis = works.stream().map(this::toWorkMini)
                .sorted(Comparator.comparing(WorkMiniDTO::score, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        minis.forEach(m -> breakdown.merge(m.type(), 1, Integer::sum));

        return new SocialProfileDTO(
                target.getId(),
                target.getUsername(),
                target.getAvatarUrl(),
                target.getBio(),
                target.getPlan().name(),
                works.size(),
                avgScore(works),
                following,
                followsYou,
                target.isPublic(),
                requested,
                false,
                followerCount,
                followingCount,
                minis.stream().limit(10).toList(),
                breakdown,
                unlockedBadges(targetId),
                minis
        );
    }

    // ── Follow ──────────────────────────────────────────────────────────

    /**
     * Alterna o vínculo com `targetId`:
     *  - já segue        → deixa de seguir
     *  - alvo público    → segue na hora (+ notificação)
     *  - alvo privado    → cria pedido pendente (ou cancela, se já pediu)
     */
    @Transactional
    public SocialUserDTO toggleFollow(Long viewerId, Long targetId) {
        if (targetId.equals(viewerId)) {
            throw new IllegalArgumentException("Você não pode seguir a si mesmo.");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        Optional<Follow> existing = followRepository.findByFollowerIdAndFollowedId(viewerId, targetId);
        if (existing.isPresent()) {
            followRepository.delete(existing.get());
        } else if (target.isPublic()) {
            followRepository.save(new Follow(viewerId, targetId));
            notificationService.onFollow(targetId, viewerId);
        } else if (followRequestRepository.existsByRequesterIdAndTargetId(viewerId, targetId)) {
            // já pediu → cancela o pedido
            followRequestRepository.deleteByRequesterIdAndTargetId(viewerId, targetId);
            notificationService.clearFollowRequest(targetId, viewerId);
        } else {
            followRequestRepository.save(new FollowRequest(viewerId, targetId));
            notificationService.onFollowRequest(targetId, viewerId);
        }

        return toSocialUser(target, viewerId);
    }

    // ── Reações ─────────────────────────────────────────────────────────

    @Transactional
    public ReactionSummaryDTO react(Long viewerId, Long feedEventId, String kindRaw) {
        FeedEvent event = feedEventRepository.findById(feedEventId)
                .orElseThrow(() -> new IllegalArgumentException("Item do feed não encontrado."));
        ReactionKind kind = ReactionKind.fromClient(kindRaw);

        ReactionKind[] previous = { null };
        feedReactionRepository.findByFeedEventIdAndUserId(feedEventId, viewerId)
                .ifPresentOrElse(existing -> {
                    previous[0] = existing.getKind();
                    if (existing.getKind() == kind) {
                        feedReactionRepository.delete(existing); // toggle off
                    } else {
                        existing.setKind(kind);
                        feedReactionRepository.save(existing);
                    }
                }, () -> feedReactionRepository.save(new FeedReaction(feedEventId, viewerId, kind)));

        // notificações: o tipo novo e (se mudou) o tipo antigo
        notificationService.syncReaction(event, kind, viewerId);
        if (previous[0] != null && previous[0] != kind) {
            notificationService.syncReaction(event, previous[0], viewerId);
        }

        return summarize(feedReactionRepository.findByFeedEventIdIn(List.of(feedEventId)), viewerId);
    }

    // ── Takes ───────────────────────────────────────────────────────────

    @Transactional
    public FeedItemDTO postTake(Long viewerId, PostTakeDTO dto) {
        String text = dto.text() == null ? "" : dto.text().trim();
        if (text.length() < TAKE_MIN) throw new IllegalArgumentException("O take está curto demais.");
        if (text.length() > TAKE_MAX) throw new IllegalArgumentException("O take passa de 280 caracteres.");

        Work work = workRepository.findById(dto.workId())
                .orElseThrow(() -> new IllegalArgumentException("Obra não encontrada."));
        if (!work.getUser().getId().equals(viewerId)) {
            throw new IllegalArgumentException("Você só pode dar take em obras suas.");
        }

        Take take = takeRepository.save(new Take(viewerId, work.getId(), text));
        FeedEvent event = feedEventService.recordTake(take, work);
        notificationService.onTakePosted(event, viewerId);
        User me = userRepository.findById(viewerId).orElseThrow();

        return toFeedItem(event, me, work, null, take, new ReactionSummaryDTO(0, 0, 0, null));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Collection<Long> feedActorIds(Long viewerId) {
        Set<Long> ids = new HashSet<>(followRepository.findFollowedIds(viewerId));
        ids.add(viewerId);
        return ids;
    }

    private FeedItemDTO toFeedItem(FeedEvent e, User actor, Work work, Badge badge, Take take,
                                   ReactionSummaryDTO reactions) {
        return new FeedItemDTO(
                e.getId(),
                e.getType().name(),
                actor == null ? null : new ActorDTO(actor.getId(), actor.getUsername(), actor.getAvatarUrl()),
                e.getCreatedAt(),
                work == null ? null : toWorkMini(work),
                badge == null ? null : new BadgeMiniDTO(badge.getCode(), badge.getName(), badge.getIcon()),
                take == null ? null : take.getText(),
                e.getScore(),
                reactions
        );
    }

    private WorkMiniDTO toWorkMini(Work w) {
        String type = WorkTypeResolver.fromCategoryName(
                w.getCategory() != null ? w.getCategory().getName() : null);
        return new WorkMiniDTO(w.getId(), w.getTitle(), type, w.getImageUrl(), w.getScore());
    }

    private SocialUserDTO toSocialUser(User u, Long viewerId) {
        List<Work> works = workRepository.findByUserId(u.getId());
        boolean self = u.getId().equals(viewerId);
        boolean following = !self && followRepository.existsByFollowerIdAndFollowedId(viewerId, u.getId());
        boolean requested = !self && !following && !u.isPublic()
                && followRequestRepository.existsByRequesterIdAndTargetId(viewerId, u.getId());
        return new SocialUserDTO(
                u.getId(),
                u.getUsername(),
                u.getAvatarUrl(),
                u.getBio(),
                u.getPlan().name(),
                works.size(),
                avgScore(works),
                following,
                !self && followRepository.existsByFollowerIdAndFollowedId(u.getId(), viewerId),
                u.isPublic(),
                requested
        );
    }

    private List<BadgeMiniDTO> unlockedBadges(Long userId) {
        List<UserBadge> unlocked = userBadgeRepository.findByUserId(userId).stream()
                .filter(ub -> ub.getUnlockedAt() != null)
                .sorted(Comparator.comparing(UserBadge::getUnlockedAt).reversed())
                .toList();
        if (unlocked.isEmpty()) return List.of();
        Map<Long, Badge> byId = byId(
                badgeRepository.findAllById(unlocked.stream().map(UserBadge::getBadgeId).toList()),
                Badge::getId);
        return unlocked.stream()
                .map(ub -> byId.get(ub.getBadgeId()))
                .filter(Objects::nonNull)
                .map(b -> new BadgeMiniDTO(b.getCode(), b.getName(), b.getIcon()))
                .toList();
    }

    private static ReactionSummaryDTO summarize(List<FeedReaction> reactions, Long viewerId) {
        long up = 0, agree = 0, disagree = 0;
        String mine = null;
        for (FeedReaction r : reactions) {
            switch (r.getKind()) {
                case UP -> up++;
                case AGREE -> agree++;
                case DISAGREE -> disagree++;
            }
            if (r.getUserId().equals(viewerId)) mine = r.getKind().toClient();
        }
        return new ReactionSummaryDTO(up, agree, disagree, mine);
    }

    private static double avgScore(List<Work> works) {
        if (works.isEmpty()) return 0.0;
        double avg = works.stream()
                .map(Work::getScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        return Math.round(avg * 10) / 10.0;
    }

    private static Set<Long> idsOf(List<FeedEvent> events, Function<FeedEvent, Long> getter) {
        return events.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(Iterable<T> items, Function<T, Long> idGetter) {
        Map<Long, T> map = new HashMap<>();
        items.forEach(i -> map.put(idGetter.apply(i), i));
        return map;
    }
}
