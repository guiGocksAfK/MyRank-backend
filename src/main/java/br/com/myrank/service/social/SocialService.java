package br.com.myrank.service.social;

import br.com.myrank.domain.entity.*;
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
    private final FeedEventRepository feedEventRepository;
    private final FeedReactionRepository feedReactionRepository;
    private final TakeRepository takeRepository;
    private final WorkRepository workRepository;
    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BadgeRepository badgeRepository;
    private final FeedEventService feedEventService;

    public SocialService(FollowRepository followRepository,
                         FeedEventRepository feedEventRepository,
                         FeedReactionRepository feedReactionRepository,
                         TakeRepository takeRepository,
                         WorkRepository workRepository,
                         UserRepository userRepository,
                         UserBadgeRepository userBadgeRepository,
                         BadgeRepository badgeRepository,
                         FeedEventService feedEventService) {
        this.followRepository = followRepository;
        this.feedEventRepository = feedEventRepository;
        this.feedReactionRepository = feedReactionRepository;
        this.takeRepository = takeRepository;
        this.workRepository = workRepository;
        this.userRepository = userRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.badgeRepository = badgeRepository;
        this.feedEventService = feedEventService;
    }

    // ── Resumo ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SocialSummaryDTO getSummary(Long viewerId) {
        long following = followRepository.countByFollowerId(viewerId);
        long followers = followRepository.countByFollowedId(viewerId);
        long feedCount = feedEventRepository.countFeed(feedActorIds(viewerId));
        return new SocialSummaryDTO(following, followers, feedCount);
    }

    // ── Feed ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FeedItemDTO> getFeed(Long viewerId, int page, int size) {
        int capped = Math.min(Math.max(size, 1), 50);
        List<FeedEvent> events = feedEventRepository.findFeed(
                feedActorIds(viewerId), PageRequest.of(Math.max(page, 0), capped));
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
        Set<Long> followed = new HashSet<>(followRepository.findFollowedIds(viewerId));
        return userRepository.searchByUsername(viewerId, q, PageRequest.of(0, MAX_LIST)).stream()
                .filter(u -> u.isPublic() || followed.contains(u.getId()))
                .map(u -> toSocialUser(u, viewerId))
                .toList();
    }

    // ── Perfil ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SocialProfileDTO getProfile(Long viewerId, Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        boolean self = targetId.equals(viewerId);
        boolean following = !self && followRepository.existsByFollowerIdAndFollowedId(viewerId, targetId);
        boolean followsYou = !self && followRepository.existsByFollowerIdAndFollowedId(targetId, viewerId);

        if (!self && !target.isPublic() && !following) {
            throw new IllegalArgumentException("Este perfil é privado.");
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
                minis.stream().limit(10).toList(),
                breakdown,
                unlockedBadges(targetId),
                minis
        );
    }

    // ── Follow ──────────────────────────────────────────────────────────

    @Transactional
    public SocialUserDTO toggleFollow(Long viewerId, Long targetId) {
        if (targetId.equals(viewerId)) {
            throw new IllegalArgumentException("Você não pode seguir a si mesmo.");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        followRepository.findByFollowerIdAndFollowedId(viewerId, targetId)
                .ifPresentOrElse(
                        followRepository::delete,
                        () -> followRepository.save(new Follow(viewerId, targetId)));

        return toSocialUser(target, viewerId);
    }

    // ── Reações ─────────────────────────────────────────────────────────

    @Transactional
    public ReactionSummaryDTO react(Long viewerId, Long feedEventId, String kindRaw) {
        if (!feedEventRepository.existsById(feedEventId)) {
            throw new IllegalArgumentException("Item do feed não encontrado.");
        }
        ReactionKind kind = ReactionKind.fromClient(kindRaw);

        feedReactionRepository.findByFeedEventIdAndUserId(feedEventId, viewerId)
                .ifPresentOrElse(existing -> {
                    if (existing.getKind() == kind) {
                        feedReactionRepository.delete(existing); // toggle off
                    } else {
                        existing.setKind(kind);
                        feedReactionRepository.save(existing);
                    }
                }, () -> feedReactionRepository.save(new FeedReaction(feedEventId, viewerId, kind)));

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
        return new SocialUserDTO(
                u.getId(),
                u.getUsername(),
                u.getAvatarUrl(),
                u.getBio(),
                u.getPlan().name(),
                works.size(),
                avgScore(works),
                !self && followRepository.existsByFollowerIdAndFollowedId(viewerId, u.getId()),
                !self && followRepository.existsByFollowerIdAndFollowedId(u.getId(), viewerId)
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
