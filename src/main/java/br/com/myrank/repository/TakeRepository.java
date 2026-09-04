package br.com.myrank.repository;

import br.com.myrank.domain.entity.Take;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TakeRepository extends JpaRepository<Take, Long> {
}
