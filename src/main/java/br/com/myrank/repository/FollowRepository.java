package br.com.myrank.repository;

import br.com.myrank.domain.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFollowedId(Long followerId, Long followedId);

    boolean existsByFollowerIdAndFollowedId(Long followerId, Long followedId);

    long countByFollowerId(Long followerId);

    long countByFollowedId(Long followedId);

    @Query("select f.followedId from Follow f where f.followerId = :userId")
    List<Long> findFollowedIds(Long userId);

    @Query("select f.followerId from Follow f where f.followedId = :userId")
    List<Long> findFollowerIds(Long userId);
}
