package br.com.myrank.repository;

import br.com.myrank.domain.entity.FeedEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface FeedEventRepository extends JpaRepository<FeedEvent, Long> {

    @Query("select e from FeedEvent e where e.userId in :userIds order by e.createdAt desc, e.id desc")
    List<FeedEvent> findFeed(Collection<Long> userIds, Pageable pageable);

    @Query("select count(e) from FeedEvent e where e.userId in :userIds")
    long countFeed(Collection<Long> userIds);

    boolean existsByTypeAndWorkId(br.com.myrank.domain.enums.FeedEventType type, Long workId);
}
