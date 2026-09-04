package br.com.myrank.repository;

import br.com.myrank.domain.entity.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {
}
