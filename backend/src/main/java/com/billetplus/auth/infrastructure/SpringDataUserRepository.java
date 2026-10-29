package com.billetplus.auth.infrastructure;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/ uniquement.
 * Ne jamais l'exposer en dehors de ce package (voir JpaUserRepositoryAdapter).
 */
interface SpringDataUserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findByCreeParOrderByCreeLeAsc(UUID createurId);

    boolean existsByRole(Role role);
}
