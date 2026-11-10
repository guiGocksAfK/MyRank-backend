package br.com.myrank.repository;

import br.com.myrank.domain.entity.FeedEvent;
import br.com.myrank.domain.enums.FeedEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FeedEventRepository extends JpaRepository<FeedEvent, Long> {

    /**
     * Takes do feed (posts escritos): de quem o viewer segue + ele mesmo
     * (`userIds`) e, com `everyone`, de qualquer perfil público (timeline geral).
     * Sem `everyone` é o "Só quem eu sigo". As avaliações entram à parte,
     * agrupadas (ver {@link #findRatingsBetween}). `takeType` vai como parâmetro
     * de propósito — enum PG por literal em @Query gera cast quebrado no Hibernate.
     */
    @Query("""
            select e from FeedEvent e
            where e.type = :takeType
              and (e.userId in :userIds
                   or (:everyone = true
                       and exists (select 1 from User u where u.id = e.userId and u.isPublic = true)))
            order by e.createdAt desc, e.id desc
            """)
    List<FeedEvent> findFeed(Collection<Long> userIds, boolean everyone, FeedEventType takeType, Pageable pageable);

    @Query("""
            select count(e) from FeedEvent e
            where e.type = :takeType
              and (e.userId in :userIds
                   or exists (select 1 from User u where u.id = e.userId and u.isPublic = true))
            """)
    long countFeed(Collection<Long> userIds, FeedEventType takeType);

    /**
     * Avaliações (obra adicionada ou nota mudada) das mesmas pessoas do feed
     * (mesma regra de `everyone`), dentro de (`after`, `before`]. O feed junta
     * isso num card por pessoa por dia.
     */
    @Query("""
            select e from FeedEvent e
            where e.type in :types
              and e.workId is not null
              and e.createdAt > :after and e.createdAt <= :before
              and (e.userId in :userIds
                   or (:everyone = true
                       and exists (select 1 from User u where u.id = e.userId and u.isPublic = true)))
            order by e.createdAt desc, e.id desc
            """)
    List<FeedEvent> findRatingsBetween(Collection<Long> userIds, boolean everyone, Collection<FeedEventType> types,
                                       LocalDateTime after, LocalDateTime before, Pageable pageable);

    java.util.Optional<FeedEvent> findByTakeId(Long takeId);
}
