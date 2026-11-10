package br.com.myrank.service.social;

import br.com.myrank.domain.entity.*;
import br.com.myrank.domain.enums.FeedEventType;
import br.com.myrank.domain.enums.ReactionKind;
import br.com.myrank.dto.*;
import br.com.myrank.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SocialService {

    private static final int MAX_LIST = 20;
    /** Quantas pessoas entram na conta de sugestões e de "pra debater" (as mais novas). */
    private static final int TASTE_POOL = 200;
    private static final int DEBATE_MAX = 10;
    /** Acima disso a pessoa já é parecida demais pra ser "pra debater". */
    private static final int DEBATE_MAX_PCT = 60;
    private static final int TAKE_MIN = 3;
    private static final int TAKE_MAX = 280;
    /** Avaliações mais velhas que isso não viram card: o backfill criou um evento por obra antiga. */
    private static final int RATED_DAY_WINDOW_DAYS = 30;
    /** Capas por card "avaliou N obras". */
    private static final int RATED_DAY_COVERS = 8;
    /** Teto de eventos lidos pra montar os cards de uma página (uma importação grande não pesa). */
    private static final int RATED_DAY_SCAN_MAX = 2000;
    private static final List<FeedEventType> RATING_TYPES = List.of(FeedEventType.ADDED, FeedEventType.RATED);

    private final FollowRepository followRepository;
    private final FollowRequestRepository followRequestRepository;
    private final FeedEventRepository feedEventRepository;
    private final FeedReactionRepository feedReactionRepository;
    private final TakeRepository takeRepository;
    private final TakeCommentRepository takeCommentRepository;
    private final WorkRepository workRepository;
    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BadgeRepository badgeRepository;
    private final FeedEventService feedEventService;
    private final NotificationService notificationService;
    private final Clock clock;

    public SocialService(FollowRepository followRepository,
                         FollowRequestRepository followRequestRepository,
                         FeedEventRepository feedEventRepository,
                         FeedReactionRepository feedReactionRepository,
                         TakeRepository takeRepository,
                         TakeCommentRepository takeCommentRepository,
                         WorkRepository workRepository,
                         UserRepository userRepository,
                         UserBadgeRepository userBadgeRepository,
                         BadgeRepository badgeRepository,
                         FeedEventService feedEventService,
                         NotificationService notificationService,
                         Clock clock) {
        this.followRepository = followRepository;
        this.followRequestRepository = followRequestRepository;
        this.feedEventRepository = feedEventRepository;
        this.feedReactionRepository = feedReactionRepository;
        this.takeRepository = takeRepository;
        this.takeCommentRepository = takeCommentRepository;
        this.workRepository = workRepository;
        this.userRepository = userRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.badgeRepository = badgeRepository;
        this.feedEventService = feedEventService;
        this.notificationService = notificationService;
        this.clock = clock;
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

    /**
     * Takes, paginados como sempre, e as avaliações do mesmo período juntadas num
     * card por pessoa por dia ("avaliou 5 obras"). Cada página leva as avaliações
     * entre o último take da página anterior e o último desta; a última página
     * (ou um feed sem takes) vai até {@link #RATED_DAY_WINDOW_DAYS} dias atrás.
     */
    @Transactional(readOnly = true)
    public List<FeedItemDTO> getFeed(Long viewerId, int page, int size) {
        int capped = Math.min(Math.max(size, 1), 50);
        int p = Math.max(page, 0);
        Collection<Long> actors = feedActorIds(viewerId);
        List<FeedEvent> events = feedEventRepository.findFeed(actors, FeedEventType.TAKE, PageRequest.of(p, capped));

        LocalDateTime floor = LocalDateTime.now(clock).minusDays(RATED_DAY_WINDOW_DAYS);
        // folga de um dia: o relógio é UTC e o evento grava a hora local do servidor
        LocalDateTime before = LocalDateTime.now(clock).plusDays(1);
        if (p > 0) {
            // o último take da página anterior (página de tamanho 1 = deslocamento exato)
            List<FeedEvent> prev = feedEventRepository.findFeed(
                    actors, FeedEventType.TAKE, PageRequest.of(p * capped - 1, 1));
            if (prev.isEmpty()) return List.of();
            before = prev.get(0).getCreatedAt();
        }
        LocalDateTime after = floor;
        if (events.size() == capped && events.get(events.size() - 1).getCreatedAt().isAfter(floor)) {
            after = events.get(events.size() - 1).getCreatedAt();
        }

        List<FeedItemDTO> items = new ArrayList<>(takeItems(events, viewerId));
        if (after.isBefore(before)) items.addAll(ratedDays(actors, after, before));
        items.sort(Comparator.comparing(FeedItemDTO::createdAt).reversed());
        return items;
    }

    private List<FeedItemDTO> takeItems(List<FeedEvent> events, Long viewerId) {
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

        Set<Long> takeIds = idsOf(events, FeedEvent::getTakeId);
        Map<Long, Integer> commentCounts = new HashMap<>();
        if (!takeIds.isEmpty()) {
            takeCommentRepository.countByTakeIdIn(takeIds).forEach(row ->
                    commentCounts.put((Long) row[0], ((Number) row[1]).intValue()));
        }

        return events.stream().map(e -> toFeedItem(
                e,
                users.get(e.getUserId()),
                e.getWorkId() != null ? works.get(e.getWorkId()) : null,
                e.getBadgeId() != null ? badges.get(e.getBadgeId()) : null,
                e.getTakeId() != null ? takes.get(e.getTakeId()) : null,
                e.getTakeId() != null ? commentCounts.getOrDefault(e.getTakeId(), 0) : 0,
                viewerId,
                summarize(reactionsByEvent.getOrDefault(e.getId(), List.of()), viewerId)
        )).toList();
    }

    /** Um card por pessoa por dia com as obras avaliadas (distintas, a mais recente primeiro). */
    private List<FeedItemDTO> ratedDays(Collection<Long> actors, LocalDateTime after, LocalDateTime before) {
        List<FeedEvent> ratings = feedEventRepository.findRatingsBetween(
                actors, RATING_TYPES, after, before, PageRequest.of(0, RATED_DAY_SCAN_MAX));
        if (ratings.isEmpty()) return List.of();

        // já vem do mais novo pro mais velho: o 1º evento de cada grupo é o mais recente
        Map<String, List<FeedEvent>> groups = new LinkedHashMap<>();
        for (FeedEvent e : ratings) {
            groups.computeIfAbsent(e.getUserId() + "|" + e.getCreatedAt().toLocalDate(), k -> new ArrayList<>()).add(e);
        }
        Map<String, List<Long>> workIds = new LinkedHashMap<>();
        Set<Long> toLoad = new HashSet<>();
        groups.forEach((key, list) -> {
            List<Long> ids = list.stream().map(FeedEvent::getWorkId).distinct().toList();
            workIds.put(key, ids);
            toLoad.addAll(ids.subList(0, Math.min(ids.size(), RATED_DAY_COVERS)));
        });
        Map<Long, Work> works = byId(workRepository.findAllById(toLoad), Work::getId);
        Map<Long, User> users = byId(userRepository.findAllById(
                ratings.stream().map(FeedEvent::getUserId).collect(Collectors.toSet())), User::getId);

        List<FeedItemDTO> items = new ArrayList<>();
        groups.forEach((key, list) -> {
            FeedEvent latest = list.get(0);
            User actor = users.get(latest.getUserId());
            List<Long> ids = workIds.get(key);
            List<WorkMiniDTO> minis = ids.stream()
                    .limit(RATED_DAY_COVERS)
                    .map(works::get).filter(Objects::nonNull)
                    .map(this::toWorkMini)
                    .toList();
            if (actor == null || minis.isEmpty()) return; // obra apagada ou conta removida
            items.add(new FeedItemDTO(
                    -latest.getId(),
                    "RATED_DAY",
                    new ActorDTO(actor.getId(), actor.getUsername(), actor.getAvatarUrl()),
                    latest.getCreatedAt(),
                    null, null, null, null, 0, false, false, null, null,
                    minis,
                    ids.size()));
        });
        return items;
    }

    // ── Listas de usuários ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SocialUserDTO> getFollowing(Long viewerId) {
        List<TasteMatch.Rated> mine = myRated(viewerId);
        return userRepository.findAllById(followRepository.findFollowedIds(viewerId)).stream()
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase()))
                .map(u -> toSocialUser(u, viewerId, mine))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SocialUserDTO> getFollowers(Long viewerId) {
        List<TasteMatch.Rated> mine = myRated(viewerId);
        return userRepository.findAllById(followRepository.findFollowerIds(viewerId)).stream()
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase()))
                .map(u -> toSocialUser(u, viewerId, mine))
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

    /**
     * Quem você ainda não segue, do gosto mais parecido pro menos. A conta roda
     * sobre as {@link #TASTE_POOL} contas mais novas (com as obras de todas
     * carregadas de uma vez) e devolve as {@link #MAX_LIST} melhores.
     */
    @Transactional(readOnly = true)
    public List<SocialUserDTO> getSuggestions(Long viewerId) {
        Set<Long> excluded = new HashSet<>(followRepository.findFollowedIds(viewerId));
        excluded.addAll(followRequestRepository.findRequestedTargetIdsAll(viewerId)); // já pediu
        if (excluded.isEmpty()) excluded.add(-1L);
        List<TasteMatch.Rated> mine = myRated(viewerId);
        List<User> pool = userRepository.findSuggestions(viewerId, excluded, PageRequest.of(0, TASTE_POOL));
        // privado que você não segue: as obras são fechadas, então não entra na conta
        Map<Long, TasteMatch.Result> taste = tasteOf(mine, pool.stream().filter(User::isPublic).toList());
        Comparator<User> best = Comparator
                .comparing((User u) -> taste.getOrDefault(u.getId(), TasteMatch.Result.NONE).pct(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(u -> taste.getOrDefault(u.getId(), TasteMatch.Result.NONE).shared(),
                        Comparator.reverseOrder());
        return pool.stream()
                .sorted(best)
                .limit(MAX_LIST)
                .map(u -> toSocialUser(u, viewerId, mine))
                .toList();
    }

    /**
     * "Pra debater": quem tem o gosto mais diferente do seu, com pelo menos
     * {@link TasteMatch#DEBATE_MIN_SHARED} obras em comum (discordar com poucas é
     * acaso) e % até {@link #DEBATE_MAX_PCT}. Inclui quem você já segue.
     */
    @Transactional(readOnly = true)
    public List<SocialUserDTO> getDebaters(Long viewerId) {
        Set<Long> following = new HashSet<>(followRepository.findFollowedIds(viewerId));
        List<TasteMatch.Rated> mine = myRated(viewerId);
        List<User> pool = userRepository.findSuggestions(viewerId, Set.of(-1L), PageRequest.of(0, TASTE_POOL))
                .stream().filter(u -> u.isPublic() || following.contains(u.getId())).toList();
        Map<Long, TasteMatch.Result> taste = tasteOf(mine, pool);
        return pool.stream()
                .filter(u -> {
                    TasteMatch.Result r = taste.get(u.getId());
                    return r != null && r.pct() != null && r.shared() >= TasteMatch.DEBATE_MIN_SHARED
                            && r.pct() <= DEBATE_MAX_PCT;
                })
                .sorted(Comparator.comparing((User u) -> taste.get(u.getId()).pct()))
                .limit(DEBATE_MAX)
                .map(u -> toSocialUser(u, viewerId, mine))
                .toList();
    }

    /** Gosto parecido de várias pessoas, com as obras de todas numa consulta só. */
    private Map<Long, TasteMatch.Result> tasteOf(List<TasteMatch.Rated> mine, List<User> people) {
        if (mine.isEmpty() || people.isEmpty()) return Map.of();
        Map<Long, List<Work>> worksByUser = workRepository
                .findByUserIdIn(people.stream().map(User::getId).toList()).stream()
                .collect(Collectors.groupingBy(w -> w.getUser().getId()));
        Map<Long, TasteMatch.Result> out = new HashMap<>();
        worksByUser.forEach((id, works) -> out.put(id, TasteMatch.compare(mine, rated(works))));
        return out;
    }

    @Transactional(readOnly = true)
    public List<SocialUserDTO> searchUsers(Long viewerId, String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();
        // perfis privados também aparecem — pra poder pedir pra seguir; o conteúdo
        // continua protegido no getProfile.
        List<TasteMatch.Rated> mine = myRated(viewerId);
        return userRepository.searchByUsername(viewerId, q, PageRequest.of(0, MAX_LIST)).stream()
                .map(u -> toSocialUser(u, viewerId, mine))
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
        if (event.getType() != FeedEventType.TAKE) {
            throw new IllegalArgumentException("Item do feed não encontrado.");
        }
        User author = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Item do feed não encontrado."));
        if (!event.getUserId().equals(viewerId) && !author.isPublic()
                && !followRepository.existsByFollowerIdAndFollowedId(viewerId, author.getId())) {
            throw new IllegalArgumentException("Item do feed não encontrado.");
        }
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

        return toFeedItem(event, me, work, null, take, 0, viewerId, new ReactionSummaryDTO(0, 0, 0, null));
    }

    @Transactional
    public FeedItemDTO editTake(Long viewerId, Long takeId, String textRaw) {
        Take take = takeRepository.findById(takeId)
                .orElseThrow(() -> new IllegalArgumentException("Take não encontrado."));
        if (!take.getUserId().equals(viewerId)) {
            throw new IllegalArgumentException("Você só pode editar os seus takes.");
        }
        String text = textRaw == null ? "" : textRaw.trim();
        if (text.length() < TAKE_MIN) throw new IllegalArgumentException("O take está curto demais.");
        if (text.length() > TAKE_MAX) throw new IllegalArgumentException("O take passa de 280 caracteres.");

        take.setText(text);
        take = takeRepository.save(take); // @PreUpdate bumpa updatedAt

        FeedEvent event = feedEventRepository.findByTakeId(takeId)
                .orElseThrow(() -> new IllegalArgumentException("Evento do take não encontrado."));
        Work work = workRepository.findById(take.getWorkId()).orElse(null);
        User author = userRepository.findById(viewerId).orElseThrow();
        int comments = (int) takeCommentRepository.countByTakeId(takeId);
        ReactionSummaryDTO reactions = summarize(
                feedReactionRepository.findByFeedEventIdIn(List.of(event.getId())), viewerId);
        return toFeedItem(event, author, work, null, take, comments, viewerId, reactions);
    }

    @Transactional
    public void deleteTake(Long viewerId, Long takeId) {
        Take take = takeRepository.findById(takeId)
                .orElseThrow(() -> new IllegalArgumentException("Take não encontrado."));
        if (!take.getUserId().equals(viewerId)) {
            throw new IllegalArgumentException("Você só pode apagar os seus takes.");
        }
        takeRepository.delete(take); // feed_event / comentários / reações / notificações caem por FK cascade
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Collection<Long> feedActorIds(Long viewerId) {
        Set<Long> ids = new HashSet<>(followRepository.findFollowedIds(viewerId));
        ids.add(viewerId);
        return ids;
    }

    private FeedItemDTO toFeedItem(FeedEvent e, User actor, Work work, Badge badge, Take take,
                                   int commentCount, Long viewerId, ReactionSummaryDTO reactions) {
        boolean takeEdited = take != null && take.getUpdatedAt() != null
                && !take.getUpdatedAt().equals(take.getCreatedAt());
        boolean canManage = actor != null && actor.getId().equals(viewerId);
        return new FeedItemDTO(
                e.getId(),
                e.getType().name(),
                actor == null ? null : new ActorDTO(actor.getId(), actor.getUsername(), actor.getAvatarUrl()),
                e.getCreatedAt(),
                work == null ? null : toWorkMini(work),
                badge == null ? null : new BadgeMiniDTO(badge.getCode(), badge.getName(), badge.getIcon()),
                take == null ? null : take.getText(),
                take == null ? null : take.getId(),
                commentCount,
                takeEdited,
                canManage,
                e.getScore(),
                reactions,
                List.of(),
                0
        );
    }

    private WorkMiniDTO toWorkMini(Work w) {
        String type = w.getTemplate().type();
        return new WorkMiniDTO(w.getId(), w.getTitle(), type, w.getImageUrl(), w.getScore());
    }

    private SocialUserDTO toSocialUser(User u, Long viewerId) {
        return toSocialUser(u, viewerId, null);
    }

    /** {@code mine}: as obras avaliadas do viewer (null = busca agora). */
    private SocialUserDTO toSocialUser(User u, Long viewerId, List<TasteMatch.Rated> mine) {
        boolean self = u.getId().equals(viewerId);
        boolean following = !self && followRepository.existsByFollowerIdAndFollowedId(viewerId, u.getId());
        boolean canSeeWorks = self || u.isPublic() || following;
        List<Work> works = canSeeWorks ? workRepository.findByUserId(u.getId()) : List.of();
        boolean requested = !self && !following && !u.isPublic()
                && followRequestRepository.existsByRequesterIdAndTargetId(viewerId, u.getId());
        TasteMatch.Result taste = self || works.isEmpty()
                ? TasteMatch.Result.NONE
                : TasteMatch.compare(mine != null ? mine : myRated(viewerId), rated(works));
        TasteMatch.Fight fight = taste.fight();
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
                requested,
                taste.shared(),
                taste.pct(),
                fight == null ? null : fight.title(),
                fight == null ? null : fight.mine(),
                fight == null ? null : fight.theirs()
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

    // ── Gosto parecido ──────────────────────────────────────────────────
    // A conta fica na TasteMatch (a mesma do front).

    private List<TasteMatch.Rated> myRated(Long userId) {
        return rated(workRepository.findByUserId(userId));
    }

    /** Obras com nota, no formato da conta. */
    private static List<TasteMatch.Rated> rated(List<Work> works) {
        return works.stream()
                .filter(w -> w.getScore() != null && w.getTitle() != null)
                .map(w -> new TasteMatch.Rated(w.getTitle(), w.getTemplate().type(), w.getScore().doubleValue()))
                .toList();
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
