package com.billetplus.orders.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/persistence uniquement.
 * Le reste de l'application passe par le port OrderRepository.
 */
interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {

    /** Les lignes sont chargées dans la même requête (relation LAZY, open-in-view: false). */
    @Override
    @EntityGraph(attributePaths = "lignes")
    Optional<OrderJpaEntity> findById(UUID id);
}
