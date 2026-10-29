package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.tickets.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/persistence uniquement.
 * Le reste de l'application passe par le port TicketRepository.
 */
interface SpringDataTicketRepository extends JpaRepository<TicketJpaEntity, UUID> {

    /** Servie par l'index idx_tickets_event_id (V4). */
    List<TicketJpaEntity> findByEventIdAndStatus(UUID eventId, TicketStatus status);
}
