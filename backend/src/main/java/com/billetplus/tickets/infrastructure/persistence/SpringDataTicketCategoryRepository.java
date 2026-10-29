package com.billetplus.tickets.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/persistence uniquement.
 * Le reste de l'application passe par le port TicketCategoryRepository.
 */
interface SpringDataTicketCategoryRepository extends JpaRepository<TicketCategoryJpaEntity, UUID> {

    /** Servie par l'index idx_ticket_categories_event_id (V4). */
    List<TicketCategoryJpaEntity> findByEventIdOrderByPrixFcfaAsc(UUID eventId);
}
