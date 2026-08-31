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

    /**
     * Conversas DIRECT entre dois usuários (idealmente 0 ou 1). Devolve lista
     * ordenada por id para ser resiliente a duplicatas antigas — o chamador usa
     * a primeira. Duplicatas novas são evitadas pelo lock em {@link #lockDirectPair}.
     */
    @Query("""
            select c from Conversation c
            where c.type = :direct
              and (select count(m) from ConversationMember m
                   where m.conversationId = c.id and m.userId in (:a, :b)) = 2
            order by c.id asc
            """)
    List<Conversation> findDirectsBetween(@Param("direct") ConversationType direct,
                                          @Param("a") Long a, @Param("b") Long b);

    /**
     * Lock consultivo por transação para o par (a, b) — serializa criações de DM
     * concorrentes do mesmo par, evitando conversa duplicada numa corrida.
     * Passar os ids já ordenados (menor, maior). Postgres-only.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:a, :b)) t", nativeQuery = true)
    Integer lockDirectPair(@Param("a") int a, @Param("b") int b);

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

    /**
     * Diretório: grupos descobríveis (não-fechados) cujo nome bate com a busca,
     * populares primeiro. Exclui os grupos em que `me` já é membro.
     */
    @Query("""
            select c from Conversation c
            where c.type = :group
              and c.access <> :closed
              and (:q = '' or lower(c.name) like lower(concat('%', :q, '%')))
              and not exists (
                  select 1 from ConversationMember mm
                  where mm.conversationId = c.id and mm.userId = :me
              )
            order by (select count(m) from ConversationMember m where m.conversationId = c.id) desc,
                     c.createdAt desc
            """)
    List<Conversation> searchDirectory(@Param("group") ConversationType group,
                                       @Param("closed") ConversationAccess closed,
                                       @Param("q") String q,
                                       @Param("me") Long me,
                                       Pageable pageable);
}
