package br.com.myrank.repository;

import br.com.myrank.domain.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    List<MessageReaction> findByMessageId(Long messageId);

    List<MessageReaction> findByMessageIdIn(List<Long> messageIds);

    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    void deleteByMessageId(Long messageId);
}
