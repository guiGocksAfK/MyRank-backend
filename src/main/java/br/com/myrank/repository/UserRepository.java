package br.com.myrank.repository;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsernameAndIdNot(String username, Long id);

    @Query("""
            select u from User u
            where u.isPublic = true and u.id <> :viewerId and u.id not in :excluded
            order by u.createdAt desc
            """)
    List<User> findSuggestions(Long viewerId, Collection<Long> excluded, Pageable pageable);

    @Query("""
            select u from User u
            where u.id <> :viewerId
              and lower(u.username) like lower(concat('%', :q, '%'))
            order by u.username asc
            """)
    List<User> searchByUsername(Long viewerId, String q, Pageable pageable);
}