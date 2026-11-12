package br.com.myrank.repository;

import br.com.myrank.domain.entity.MasterTableGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MasterTableGroupRepository extends JpaRepository<MasterTableGroup, Long> {

    @Query("select distinct g from MasterTableGroup g left join fetch g.categories where g.user.id = :userId")
    List<MasterTableGroup> findByUserId(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}