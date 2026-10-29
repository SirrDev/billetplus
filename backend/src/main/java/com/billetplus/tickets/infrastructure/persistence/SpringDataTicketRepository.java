package com.billetplus.tickets.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/persistence uniquement.
 * Le reste de l'application passe par le port TicketRepository.
 */
interface SpringDataTicketRepository extends JpaRepository<TicketJpaEntity, UUID> {

    boolean existsByOrderIdAndCategoryId(UUID orderId, UUID categoryId);
}
