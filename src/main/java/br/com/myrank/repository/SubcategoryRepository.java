package br.com.myrank.repository;

import br.com.myrank.domain.entity.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {

    /** Todas as subcategorias das tabelas do usuário numa query só (GET /categories). */
    List<Subcategory> findByCategoryIdInOrderByCreatedAtAscIdAsc(Collection<Long> categoryIds);

    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);

    long countByCategoryId(Long categoryId);
}
