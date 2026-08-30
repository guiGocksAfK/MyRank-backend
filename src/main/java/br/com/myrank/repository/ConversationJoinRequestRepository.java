package br.com.myrank.repository;

import br.com.myrank.domain.entity.ConversationJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationJoinRequestRepository extends JpaRepository<ConversationJoinRequest, Long> {

    List<ConversationJoinRequest> findByConversationId(Long conversationId);

    List<ConversationJoinRequest> findByConversationIdIn(List<Long> conversationIds);

    Optional<ConversationJoinRequest> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    long countByConversationId(Long conversationId);

    List<ConversationJoinRequest> findByUserId(Long userId);
}
