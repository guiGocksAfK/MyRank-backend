package br.com.myrank.repository;

import br.com.myrank.domain.entity.ConversationBan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationBanRepository extends JpaRepository<ConversationBan, ConversationBan.Key> {

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    void deleteByConversationIdAndUserId(Long conversationId, Long userId);
}
