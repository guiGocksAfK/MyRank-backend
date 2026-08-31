package br.com.myrank.repository;

import br.com.myrank.domain.entity.TakeComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TakeCommentRepository extends JpaRepository<TakeComment, Long> {

    List<TakeComment> findByTakeIdOrderByCreatedAtAsc(Long takeId);

    long countByTakeId(Long takeId);

    /** Contagem por take (raiz + respostas) para preencher o feed em lote. */
    @Query("select c.takeId, count(c) from TakeComment c where c.takeId in :takeIds group by c.takeId")
    List<Object[]> countByTakeIdIn(Collection<Long> takeIds);
}
