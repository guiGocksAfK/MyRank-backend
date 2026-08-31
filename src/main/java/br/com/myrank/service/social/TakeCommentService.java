package br.com.myrank.service.social;

import br.com.myrank.domain.entity.FeedEvent;
import br.com.myrank.domain.entity.Take;
import br.com.myrank.domain.entity.TakeComment;
import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.ActorDTO;
import br.com.myrank.dto.TakeCommentDTO;
import br.com.myrank.repository.FeedEventRepository;
import br.com.myrank.repository.FollowRepository;
import br.com.myrank.repository.TakeCommentRepository;
import br.com.myrank.repository.TakeRepository;
import br.com.myrank.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Comentários e respostas em takes (2 níveis, estilo Instagram). */
@Service
public class TakeCommentService {

    private static final int TEXT_MAX = 500;

    private final TakeCommentRepository commentRepository;
    private final TakeRepository takeRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FeedEventRepository feedEventRepository;
    private final NotificationService notificationService;

    public TakeCommentService(TakeCommentRepository commentRepository,
                              TakeRepository takeRepository,
                              UserRepository userRepository,
                              FollowRepository followRepository,
                              FeedEventRepository feedEventRepository,
                              NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.takeRepository = takeRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.feedEventRepository = feedEventRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<TakeCommentDTO> list(Long viewerId, Long takeId) {
        Take take = requireVisibleTake(viewerId, takeId);

        List<TakeComment> all = commentRepository.findByTakeIdOrderByCreatedAtAsc(takeId);
        if (all.isEmpty()) return List.of();

        Map<Long, User> users = userRepository.findAllById(
                        all.stream().map(TakeComment::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, List<TakeComment>> repliesByParent = all.stream()
                .filter(c -> c.getParentCommentId() != null)
                .collect(Collectors.groupingBy(TakeComment::getParentCommentId));

        return all.stream()
                .filter(c -> c.getParentCommentId() == null)
                .map(root -> toDTO(root, users, viewerId, take.getUserId(),
                        repliesByParent.getOrDefault(root.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(TakeComment::getCreatedAt))
                                .map(r -> toDTO(r, users, viewerId, take.getUserId(), List.of()))
                                .toList()))
                .toList();
    }

    @Transactional
    public TakeCommentDTO add(Long viewerId, Long takeId, String textRaw, Long parentCommentId) {
        Take take = requireVisibleTake(viewerId, takeId);

        String text = textRaw == null ? "" : textRaw.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("O comentário está vazio.");
        if (text.length() > TEXT_MAX) throw new IllegalArgumentException("O comentário passa de " + TEXT_MAX + " caracteres.");

        Long rootParentId = null;
        Long replyTargetAuthor = null;
        if (parentCommentId != null) {
            TakeComment parent = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new IllegalArgumentException("Comentário não encontrado."));
            if (!parent.getTakeId().equals(takeId)) {
                throw new IllegalArgumentException("Comentário não pertence a este take.");
            }
            // achata pra 2 níveis: responder a uma resposta cai no comentário raiz dela
            rootParentId = parent.getParentCommentId() != null ? parent.getParentCommentId() : parent.getId();
            replyTargetAuthor = parent.getUserId();
        }

        TakeComment saved = commentRepository.save(
                new TakeComment(takeId, viewerId, rootParentId, text));

        // notifica o autor do take; e, se for resposta, também o autor do comentário respondido
        Long feedEventId = feedEventRepository.findByTakeId(takeId).map(FeedEvent::getId).orElse(null);
        if (feedEventId != null) {
            notificationService.onTakeComment(take.getUserId(), viewerId, feedEventId);
            if (replyTargetAuthor != null
                    && !replyTargetAuthor.equals(viewerId)
                    && !replyTargetAuthor.equals(take.getUserId())) {
                notificationService.onTakeComment(replyTargetAuthor, viewerId, feedEventId);
            }
        }

        User author = userRepository.findById(viewerId).orElseThrow();
        return toDTO(saved, Map.of(author.getId(), author), viewerId, take.getUserId(), List.of());
    }

    @Transactional
    public TakeCommentDTO edit(Long viewerId, Long commentId, String textRaw) {
        TakeComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comentário não encontrado."));
        if (!c.getUserId().equals(viewerId)) {
            throw new IllegalArgumentException("Você só pode editar os seus comentários.");
        }
        String text = textRaw == null ? "" : textRaw.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("O comentário está vazio.");
        if (text.length() > TEXT_MAX) throw new IllegalArgumentException("O comentário passa de " + TEXT_MAX + " caracteres.");

        c.setText(text);
        c.setEditedAt(java.time.LocalDateTime.now());
        commentRepository.save(c);

        Take take = takeRepository.findById(c.getTakeId()).orElseThrow();
        User author = userRepository.findById(viewerId).orElseThrow();
        return toDTO(c, Map.of(author.getId(), author), viewerId, take.getUserId(), List.of());
    }

    @Transactional
    public void delete(Long viewerId, Long commentId) {
        TakeComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comentário não encontrado."));
        Take take = takeRepository.findById(c.getTakeId())
                .orElseThrow(() -> new IllegalArgumentException("Take não encontrado."));
        if (!c.getUserId().equals(viewerId) && !take.getUserId().equals(viewerId)) {
            throw new IllegalArgumentException("Você não pode apagar este comentário.");
        }
        commentRepository.delete(c); // respostas caem por ON DELETE CASCADE
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Take requireVisibleTake(Long viewerId, Long takeId) {
        Take take = takeRepository.findById(takeId)
                .orElseThrow(() -> new IllegalArgumentException("Take não encontrado."));
        if (take.getUserId().equals(viewerId)) return take;
        User author = userRepository.findById(take.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Take não encontrado."));
        boolean visible = author.isPublic()
                || followRepository.existsByFollowerIdAndFollowedId(viewerId, author.getId());
        if (!visible) throw new IllegalArgumentException("Este take é de um perfil privado.");
        return take;
    }

    private TakeCommentDTO toDTO(TakeComment c, Map<Long, User> users, Long viewerId,
                                 Long takeAuthorId, List<TakeCommentDTO> replies) {
        User u = users.get(c.getUserId());
        boolean mine = c.getUserId().equals(viewerId);
        boolean canDelete = mine || takeAuthorId.equals(viewerId);
        return new TakeCommentDTO(
                c.getId(),
                c.getTakeId(),
                c.getParentCommentId(),
                u == null ? null : new ActorDTO(u.getId(), u.getUsername(), u.getAvatarUrl()),
                c.getText(),
                c.getCreatedAt(),
                c.getEditedAt() != null,
                mine,
                canDelete,
                replies == null ? List.of() : new ArrayList<>(replies.stream().filter(Objects::nonNull).toList())
        );
    }
}
