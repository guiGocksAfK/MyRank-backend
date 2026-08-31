package br.com.myrank.repository;

import br.com.myrank.domain.entity.FeedEvent;
import br.com.myrank.domain.enums.FeedEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface FeedEventRepository extends JpaRepository<FeedEvent, Long> {

    /**
     * Feed = só TAKEs (posts escritos). Traz os takes de quem o viewer segue +
     * ele mesmo (`userIds`) e os takes de qualquer perfil público (timeline geral).
     * RATED / ADDED / BADGE não entram no feed. `takeType` vai como parâmetro de
     * propósito — enum PG por literal em @Query gera cast quebrado no Hibernate.
     */
    @Query("""
            select e from FeedEvent e
            where e.type = :takeType
              and (e.userId in :userIds
                   or exists (select 1 from User u where u.id = e.userId and u.isPublic = true))
            order by e.createdAt desc, e.id desc
            """)
    List<FeedEvent> findFeed(Collection<Long> userIds, FeedEventType takeType, Pageable pageable);

    @Query("""
            select count(e) from FeedEvent e
            where e.type = :takeType
              and (e.userId in :userIds
                   or exists (select 1 from User u where u.id = e.userId and u.isPublic = true))
            """)
    long countFeed(Collection<Long> userIds, FeedEventType takeType);

    boolean existsByTypeAndWorkId(FeedEventType type, Long workId);
}
