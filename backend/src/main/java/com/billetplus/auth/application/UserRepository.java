package com.billetplus.auth.application;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;

import java.util.List;
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

    /** Comptes créés par cet utilisateur (les agents d'un organisateur), du plus ancien au plus récent. */
    List<User> findByCreePar(UUID createurId);

    boolean existsByRole(Role role);
}
