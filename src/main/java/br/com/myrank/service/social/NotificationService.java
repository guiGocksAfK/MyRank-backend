package br.com.myrank.service.social;

import br.com.myrank.domain.entity.*;
import br.com.myrank.domain.enums.FeedEventType;
import br.com.myrank.domain.enums.NotificationType;
import br.com.myrank.domain.enums.ReactionKind;
import br.com.myrank.dto.ActorDTO;
import br.com.myrank.dto.NotificationDTO;
import br.com.myrank.dto.WorkMiniDTO;
import br.com.myrank.repository.*;
import br.com.myrank.service.WorkTypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final FeedReactionRepository feedReactionRepository;
    private final FeedEventRepository feedEventRepository;
    private final FollowRepository followRepository;
    private final WorkRepository workRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               FeedReactionRepository feedReactionRepository,
                               FeedEventRepository feedEventRepository,
                               FollowRepository followRepository,
                               WorkRepository workRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.feedReactionRepository = feedReactionRepository;
        this.feedEventRepository = feedEventRepository;
        this.followRepository = followRepository;
        this.workRepository = workRepository;
        this.userRepository = userRepository;
    }

    // ── Gatilhos (nunca lançam pra fora) ───────────────────────────────

    /** Recalcula a notificação de reação de um tipo num evento. */
    @Transactional
    public void syncReaction(FeedEvent event, ReactionKind kind, Long actingUserId) {
        try {
            Long recipient = event.getUserId();
            if (recipient.equals(actingUserId)) return;

            long count = feedReactionRepository
                    .countByFeedEventIdAndKindAndUserIdNot(event.getId(), kind, recipient);

            Optional<Notification> existing = notificationRepository
                    .findByUserIdAndTypeAndFeedEventIdAndReactionKind(
                            recipient, NotificationType.REACTION, event.getId(), kind);

            if (count == 0) {
                existing.ifPresent(notificationRepository::delete);
                return;
            }

            Long lastActor = feedReactionRepository
                    .findFirstByFeedEventIdAndKindAndUserIdNotOrderByCreatedAtDesc(event.getId(), kind, recipient)
                    .map(FeedReaction::getUserId)
                    .orElse(actingUserId);

            Notification n = existing.orElseGet(() -> {
                Notification fresh = new Notification();
                fresh.setUserId(recipient);
                fresh.setType(NotificationType.REACTION);
                fresh.setFeedEventId(event.getId());
                fresh.setReactionKind(kind);
                return fresh;
            });
            n.setActorId(lastActor);
            n.setActorCount((int) count);
            n.setRead(false);
            notificationRepository.save(n);
        } catch (Exception e) {
            log.warn("syncReaction falhou (event {}): {}", event.getId(), e.getMessage());
        }
    }

    @Transactional
    public void onFollow(Long recipientId, Long actorId) {
        try {
            if (recipientId.equals(actorId)) return;
            Notification n = notificationRepository
                    .findByUserIdAndTypeAndActorId(recipientId, NotificationType.FOLLOW, actorId)
                    .orElseGet(() -> {
                        Notification fresh = new Notification();
                        fresh.setUserId(recipientId);
                        fresh.setType(NotificationType.FOLLOW);
                        fresh.setActorId(actorId);
                        return fresh;
                    });
            n.setActorCount(1);
            n.setRead(false);
            notificationRepository.save(n);
        } catch (Exception e) {
            log.warn("onFollow falhou: {}", e.getMessage());
        }
    }

    /** Fan-out: 1 notificação por seguidor do autor do take. */
    @Transactional
    public void onTakePosted(FeedEvent takeEvent, Long authorId) {
        try {
            List<Long> followers = followRepository.findFollowerIds(authorId);
            if (followers.isEmpty()) return;
            List<Notification> batch = new ArrayList<>();
            for (Long fid : followers) {
                if (notificationRepository.existsByUserIdAndTypeAndFeedEventId(
                        fid, NotificationType.TAKE, takeEvent.getId())) continue;
                Notification n = new Notification();
                n.setUserId(fid);
                n.setType(NotificationType.TAKE);
                n.setActorId(authorId);
                n.setFeedEventId(takeEvent.getId());
                batch.add(n);
            }
            notificationRepository.saveAll(batch);
        } catch (Exception e) {
            log.warn("onTakePosted falhou: {}", e.getMessage());
        }
    }

    // ── Leitura ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> list(Long userId, int page, int size) {
        int capped = Math.min(Math.max(size, 1), 50);
        List<Notification> rows = notificationRepository
                .findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(Math.max(page, 0), capped));
        if (rows.isEmpty()) return List.of();

        Map<Long, User> actors = userRepository.findAllById(
                        rows.stream().map(Notification::getActorId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, FeedEvent> events = feedEventRepository.findAllById(
                        rows.stream().map(Notification::getFeedEventId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(FeedEvent::getId, e -> e));
        Map<Long, Work> works = workRepository.findAllById(
                        events.values().stream().map(FeedEvent::getWorkId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Work::getId, w -> w));

        return rows.stream().map(n -> {
            User actor = n.getActorId() != null ? actors.get(n.getActorId()) : null;
            FeedEvent ev = n.getFeedEventId() != null ? events.get(n.getFeedEventId()) : null;
            Work work = ev != null && ev.getWorkId() != null ? works.get(ev.getWorkId()) : null;
            return render(n, actor, ev, work);
        }).toList();
    }

    // ── Render pt-BR ───────────────────────────────────────────────────

    private NotificationDTO render(Notification n, User actor, FeedEvent ev, Work work) {
        String actorName = actor != null ? actor.getUsername() : "Alguém";
        String noun = ev != null && ev.getType() == FeedEventType.TAKE ? "take" : "post";
        String about = work != null ? " sobre " + work.getTitle() : "";
        WorkMiniDTO workMini = work == null ? null : new WorkMiniDTO(
                work.getId(), work.getTitle(),
                WorkTypeResolver.fromCategoryName(work.getCategory() != null ? work.getCategory().getName() : null),
                work.getImageUrl(), work.getScore());

        String title;
        String message;
        String reactionKind = null;

        switch (n.getType()) {
            case REACTION -> {
                String emoji = emoji(n.getReactionKind());
                reactionKind = n.getReactionKind() != null ? n.getReactionKind().toClient() : null;
                if (n.getActorCount() <= 1) {
                    title = "Nova reação";
                    message = actorName + " reagiu " + emoji + " ao seu " + noun + about;
                } else {
                    int others = n.getActorCount() - 1;
                    title = "Reações no seu " + noun;
                    message = actorName + " e mais " + others + (others == 1 ? " pessoa" : " pessoas")
                            + " reagiram " + emoji + " ao seu " + noun + about;
                }
            }
            case FOLLOW -> {
                title = "Novo seguidor";
                message = actorName + " começou a te seguir";
            }
            case TAKE -> {
                title = "Novo take";
                message = actorName + " postou um take" + about;
            }
            default -> {
                title = "Notificação";
                message = "";
            }
        }

        return new NotificationDTO(
                n.getId(),
                n.getType().name(),
                n.isRead(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                actor == null ? null : new ActorDTO(actor.getId(), actor.getUsername(), actor.getAvatarUrl()),
                n.getActorCount(),
                reactionKind,
                workMini,
                n.getFeedEventId(),
                title,
                message
        );
    }

    private static String emoji(ReactionKind kind) {
        if (kind == null) return "";
        return switch (kind) {
            case UP -> "👍";
            case AGREE -> "🤝";
            case DISAGREE -> "👎";
        };
    }
}
