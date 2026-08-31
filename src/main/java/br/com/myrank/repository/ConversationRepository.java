package br.com.myrank.repository;

import br.com.myrank.domain.entity.Conversation;
import br.com.myrank.domain.enums.ConversationAccess;
import br.com.myrank.domain.enums.ConversationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** A conversa DIRECT entre dois usuários, se já existir. */
    @Query("""
            select c from Conversation c
            where c.type = :direct
              and (select count(m) from ConversationMember m
                   where m.conversationId = c.id and m.userId in (:a, :b)) = 2
            """)
    Optional<Conversation> findDirectBetween(@Param("direct") ConversationType direct,
                                             @Param("a") Long a, @Param("b") Long b);

    Optional<Conversation> findByInviteToken(String inviteToken);

    /** Ids dos usuários com quem `me` já tem uma conversa DIRECT. */
    @Query("""
            select distinct m2.userId
            from ConversationMember m1, ConversationMember m2, Conversation c
            where m1.conversationId = m2.conversationId
              and c.id = m1.conversationId
              and c.type = :direct
              and m1.userId = :me
              and m2.userId <> :me
            """)
    List<Long> findDirectPeerIds(@Param("me") Long me, @Param("direct") ConversationType direct);

    /** Diretório: grupos descobríveis (não-fechados) cujo nome bate com a busca, populares primeiro. */
    @Query("""
            select c from Conversation c
            where c.type = :group
              and c.access <> :closed
              and (:q = '' or lower(c.name) like lower(concat('%', :q, '%')))
            order by (select count(m) from ConversationMember m where m.conversationId = c.id) desc,
                     c.createdAt desc
            """)
    List<Conversation> searchDirectory(@Param("group") ConversationType group,
                                       @Param("closed") ConversationAccess closed,
                                       @Param("q") String q, Pageable pageable);
}
