package br.com.myrank.repository;

import br.com.myrank.domain.entity.FeedReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedReactionRepository extends JpaRepository<FeedReaction, Long> {

    List<FeedReaction> findByFeedEventIdIn(Collection<Long> feedEventIds);

    Optional<FeedReaction> findByFeedEventIdAndUserId(Long feedEventId, Long userId);
}
