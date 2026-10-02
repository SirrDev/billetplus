package com.billetplus.auth.application;

import com.billetplus.auth.domain.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Port (niveau 2) — défini par le module, jamais un JpaRepository exposé
 * directement. Voir ADR 0002, règle ArchUnit ports_ne_dependent_pas_de_spring_data.
 */
public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    User save(User user);

    boolean existsByEmail(String email);
}
