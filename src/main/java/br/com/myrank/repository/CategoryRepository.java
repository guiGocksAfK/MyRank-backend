package br.com.myrank.repository;

import br.com.myrank.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserId(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}