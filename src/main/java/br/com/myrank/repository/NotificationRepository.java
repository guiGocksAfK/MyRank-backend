package br.com.myrank.repository;

import br.com.myrank.domain.entity.Notification;
import br.com.myrank.domain.enums.NotificationType;
import br.com.myrank.domain.enums.ReactionKind;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    Optional<Notification> findByUserIdAndTypeAndFeedEventIdAndReactionKind(
            Long userId, NotificationType type, Long feedEventId, ReactionKind reactionKind);

    Optional<Notification> findByUserIdAndTypeAndActorId(
            Long userId, NotificationType type, Long actorId);

    Optional<Notification> findByUserIdAndTypeAndConversationId(
            Long userId, NotificationType type, Long conversationId);

    boolean existsByUserIdAndTypeAndFeedEventId(
            Long userId, NotificationType type, Long feedEventId);

    /** Bulk update: não dispara @PreUpdate, então updatedAt (e a ordem da lista) não muda. */
    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(Long userId);
}
