package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.domain.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
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

    /** Servie par idx_orders_telephone_cree_le (V8), préfixe telephone. */
    long countByTelephoneAndEventIdAndStatusAndExpireLeAfter(String telephone, UUID eventId, OrderStatus status,
                                                             Instant maintenant);
}
