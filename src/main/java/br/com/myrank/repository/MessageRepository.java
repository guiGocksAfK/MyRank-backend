package br.com.myrank.repository;

import br.com.myrank.domain.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Histórico de uma conversa (os dois sentidos do par), mais recentes primeiro. */
    @Query("""
            select m from Message m
            where (m.senderId = :a and m.recipientId = :b)
               or (m.senderId = :b and m.recipientId = :a)
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findConversation(@Param("a") Long a, @Param("b") Long b, Pageable pageable);

    /** Mensagens envolvendo o usuário (qualquer sentido), mais recentes primeiro. */
    @Query("""
            select m from Message m
            where m.senderId = :userId or m.recipientId = :userId
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findRecentForUser(@Param("userId") Long userId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    /** [senderId, count] das não-lidas do usuário, agrupadas por remetente. */
    @Query("""
            select m.senderId, count(m) from Message m
            where m.recipientId = :userId and m.readAt is null
            group by m.senderId
            """)
    List<Object[]> unreadCountsBySender(@Param("userId") Long userId);

    /** Marca como lidas as mensagens que `me` recebeu de `other`. */
    @Modifying
    @Query("""
            update Message m set m.readAt = :now
            where m.recipientId = :me and m.senderId = :other and m.readAt is null
            """)
    int markConversationRead(@Param("me") Long me, @Param("other") Long other, @Param("now") LocalDateTime now);
}
