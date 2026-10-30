package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.domain.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    /** Servie par l'index unique uq_orders_paiement_reference (V9). */
    @EntityGraph(attributePaths = "lignes")
    Optional<OrderJpaEntity> findByPaiementReference(String reference);

    @EntityGraph(attributePaths = "lignes")
    List<OrderJpaEntity> findByStatusOrderByPayeeLeAsc(OrderStatus status, Pageable pageable);

    @Query(
        "select distinct o.eventId from OrderJpaEntity o where o.status = :status")
    List<UUID> evenementsAvecStatut(@Param("status") OrderStatus status);

    /** Servie par idx_orders_event_id_status (V8). */
    @EntityGraph(attributePaths = "lignes")
    List<OrderJpaEntity> findByEventIdAndStatusOrderByPayeeLeAsc(UUID eventId, OrderStatus status, Pageable pageable);
}
