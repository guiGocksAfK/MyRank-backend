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

    /** Diretório: grupos descobríveis (não-fechados) cujo nome bate com a busca. */
    @Query("""
            select c from Conversation c
            where c.type = :group
              and c.access <> :closed
              and (:q = '' or lower(c.name) like lower(concat('%', :q, '%')))
            order by c.createdAt desc
            """)
    List<Conversation> searchDirectory(@Param("group") ConversationType group,
                                       @Param("closed") ConversationAccess closed,
                                       @Param("q") String q, Pageable pageable);
}
