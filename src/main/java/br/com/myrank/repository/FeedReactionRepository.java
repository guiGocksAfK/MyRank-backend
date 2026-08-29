package br.com.myrank.repository;

import br.com.myrank.domain.entity.FeedReaction;
import br.com.myrank.domain.enums.ReactionKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedReactionRepository extends JpaRepository<FeedReaction, Long> {

    List<FeedReaction> findByFeedEventIdIn(Collection<Long> feedEventIds);

    Optional<FeedReaction> findByFeedEventIdAndUserId(Long feedEventId, Long userId);

    long countByFeedEventIdAndKind(Long feedEventId, ReactionKind kind);

    /** Reações de um tipo feitas por gente que NÃO é o dono do post. */
    long countByFeedEventIdAndKindAndUserIdNot(Long feedEventId, ReactionKind kind, Long userId);

    Optional<FeedReaction> findFirstByFeedEventIdAndKindAndUserIdNotOrderByCreatedAtDesc(
            Long feedEventId, ReactionKind kind, Long userId);
}
