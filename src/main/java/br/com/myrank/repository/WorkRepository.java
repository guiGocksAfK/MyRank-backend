package br.com.myrank.repository;

import br.com.myrank.domain.entity.Work;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkRepository extends JpaRepository<Work, Long> {

    List<Work> findByCategoryId(Long categoryId);

    List<Work> findByUserId(Long userId);

    List<Work> findByUserIdOrderByFinalScoreDesc(Long userId);
}