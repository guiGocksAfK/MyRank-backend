package br.com.myrank.repository;

import br.com.myrank.domain.entity.MasterTableGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MasterTableGroupRepository extends JpaRepository<MasterTableGroup, Long> {

    List<MasterTableGroup> findByUserId(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}