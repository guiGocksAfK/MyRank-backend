package br.com.myrank.repository;

import br.com.myrank.domain.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    List<ConversationMember> findByUserId(Long userId);

    List<ConversationMember> findByConversationId(Long conversationId);

    List<ConversationMember> findByConversationIdIn(List<Long> conversationIds);

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    long countByConversationId(Long conversationId);

    @Query("select m.userId from ConversationMember m where m.conversationId = :conversationId")
    List<Long> findMemberIds(@Param("conversationId") Long conversationId);
}
