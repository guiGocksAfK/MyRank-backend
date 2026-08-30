package br.com.myrank.repository;

import br.com.myrank.domain.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Histórico de uma conversa, mais recentes primeiro. */
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    /** Últimas mensagens de várias conversas (uma por conversa). */
    @Query("""
            select m from Message m
            where m.id in (
                select max(m2.id) from Message m2 where m2.conversationId in :conversationIds group by m2.conversationId
            )
            """)
    List<Message> findLastPerConversation(@Param("conversationIds") List<Long> conversationIds);

    /** Não-lidas de um membro numa conversa: id > cursor e não enviadas por ele. */
    @Query("""
            select count(m) from Message m
            where m.conversationId = :conversationId
              and m.senderId <> :userId
              and (:cursor is null or m.id > :cursor)
            """)
    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("userId") Long userId,
                     @Param("cursor") Long cursor);

    /** Total de não-lidas do usuário em todas as conversas. */
    @Query("""
            select count(m) from Message m, ConversationMember cm
            where cm.conversationId = m.conversationId
              and cm.userId = :userId
              and m.senderId <> :userId
              and (cm.lastReadMessageId is null or m.id > cm.lastReadMessageId)
            """)
    long countUnreadTotal(@Param("userId") Long userId);
}
