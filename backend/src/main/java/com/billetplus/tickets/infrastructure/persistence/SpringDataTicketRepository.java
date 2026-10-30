package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.tickets.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/persistence uniquement.
 * Le reste de l'application passe par le port TicketRepository.
 */
interface SpringDataTicketRepository extends JpaRepository<TicketJpaEntity, UUID> {

    /** Servie par l'index idx_tickets_event_id (V4). */
    List<TicketJpaEntity> findByEventIdAndStatus(UUID eventId, TicketStatus status);

    /** Servie par idx_tickets_order_id_category_id (V4), préfixe order_id. */
    List<TicketJpaEntity> findByOrderId(UUID orderId);

    /** Servie par idx_tickets_event_id (V4). Chaque ligne : categoryId, statut, nombre. */
    @Query("select t.categoryId, t.status, count(t) from TicketJpaEntity t where t.eventId = :eventId "
        + "group by t.categoryId, t.status")
    List<Object[]> compterParCategorieEtStatut(@Param("eventId") UUID eventId);
}
