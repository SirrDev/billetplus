package com.billetplus.tickets.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/persistence uniquement.
 * Le reste de l'application passe par le port TicketCategoryRepository.
 */
interface SpringDataTicketCategoryRepository extends JpaRepository<TicketCategoryJpaEntity, UUID> {

    /** Servie par l'index idx_ticket_categories_event_id (V4). */
    List<TicketCategoryJpaEntity> findByEventIdOrderByPrixFcfaAsc(UUID eventId);

    /**
     * Requête native : la condition et l'incrément sont évalués par PostgreSQL sur
     * la ligne verrouillée. Le CHECK chk_ticket_categories_quota_vendu (V4) reste
     * le dernier rempart.
     */
    @Modifying
    @Query(value = "UPDATE ticket_categories SET quota_vendu = quota_vendu + :quantite, version = version + 1 "
        + "WHERE id = :id AND quota_vendu + :quantite <= quota_total", nativeQuery = true)
    int consommerQuota(@Param("id") UUID id, @Param("quantite") int quantite);
}
