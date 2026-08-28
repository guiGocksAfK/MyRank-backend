package br.com.myrank.repository;

import br.com.myrank.domain.entity.Take;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TakeRepository extends JpaRepository<Take, Long> {

    List<Take> findByWorkIdOrderByCreatedAtDesc(Long workId);
}
