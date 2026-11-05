package br.com.myrank.repository;

import br.com.myrank.domain.entity.UserCreator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserCreatorRepository extends JpaRepository<UserCreator, Long> {

    List<UserCreator> findByUserIdAndCreatorKeyIn(Long userId, Collection<String> creatorKeys);

    Optional<UserCreator> findByUserIdAndCreatorKey(Long userId, String creatorKey);
}
