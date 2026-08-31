package br.com.myrank.repository;

import br.com.myrank.domain.entity.FollowRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {

    Optional<FollowRequest> findByRequesterIdAndTargetId(Long requesterId, Long targetId);

    boolean existsByRequesterIdAndTargetId(Long requesterId, Long targetId);

    void deleteByRequesterIdAndTargetId(Long requesterId, Long targetId);

    long countByTargetId(Long targetId);

    /** Ids de quem pediu pra seguir `targetId`, do mais recente ao mais antigo. */
    @Query("select r.requesterId from FollowRequest r where r.targetId = :targetId order by r.createdAt desc")
    List<Long> findRequesterIds(Long targetId);

    /** Dos ids dados, quais `me` já solicitou seguir (pendentes). */
    @Query("select r.targetId from FollowRequest r where r.requesterId = :me and r.targetId in :targetIds")
    List<Long> findRequestedTargetIds(Long me, List<Long> targetIds);

    /** Todos os ids que `me` já solicitou seguir (pendentes). */
    @Query("select r.targetId from FollowRequest r where r.requesterId = :me")
    List<Long> findRequestedTargetIdsAll(Long me);
}
