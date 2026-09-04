package br.com.myrank.repository;

import br.com.myrank.domain.entity.Work;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkRepository extends JpaRepository<Work, Long> {

    /*
     * `left join fetch w.category` em todos os finders de lista: sem isso, mapear
     * cada Work pro DTO dispara 1 query por obra pra pegar o nome da categoria
     * (N+1). Com o fetch, é 1 query só. `@ManyToOne` sem paginação → fetch join
     * é seguro. `left` pra não sumir obra com categoria nula.
     */

    @Query("select w from Work w left join fetch w.category where w.category.id = :categoryId")
    List<Work> findByCategoryId(Long categoryId);

    @Query("select w from Work w left join fetch w.category where w.user.id = :userId")
    List<Work> findByUserId(Long userId);

    @Query("""
            select w from Work w
            left join fetch w.category
            where w.user.id = :userId
            order by w.finalScore desc
            """)
    List<Work> findByUserIdOrderByFinalScoreDesc(Long userId);
}
