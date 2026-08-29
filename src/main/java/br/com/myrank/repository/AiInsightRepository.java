package br.com.myrank.repository;

import br.com.myrank.domain.entity.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    Optional<AiInsight> findByUserIdAndSelectionHash(Long userId, String selectionHash);

    Optional<AiInsight> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
