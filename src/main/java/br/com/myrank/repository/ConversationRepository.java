package br.com.myrank.repository;

import br.com.myrank.domain.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** A conversa DIRECT entre dois usuários, se já existir. */
    @Query("""
            select c from Conversation c
            where c.type = br.com.myrank.domain.enums.ConversationType.DIRECT
              and (select count(m) from ConversationMember m
                   where m.conversationId = c.id and m.userId in (:a, :b)) = 2
            """)
    Optional<Conversation> findDirectBetween(@Param("a") Long a, @Param("b") Long b);
}
