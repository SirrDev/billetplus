package com.billetplus.auth.infrastructure;

import com.billetplus.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/ uniquement.
 * Ne jamais l'exposer en dehors de ce package (voir JpaUserRepositoryAdapter).
 */
interface SpringDataUserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
