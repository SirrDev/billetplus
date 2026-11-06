package com.billetplus.orders.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Interface Spring Data — usage interne à infrastructure/ uniquement. */
interface SpringDataReversementRepository extends JpaRepository<ReversementJpaEntity, UUID> {

    boolean existsByEventId(UUID eventId);

    @Query("select coalesce(sum(r.montantFcfa), 0) from ReversementJpaEntity r where r.eventId = :eventId")
    long totalReverse(@Param("eventId") UUID eventId);

    /** Lignes (eventId, somme) : une par événement qui a au moins un reversement. */
    @Query("select r.eventId, sum(r.montantFcfa) from ReversementJpaEntity r group by r.eventId")
    List<Object[]> totauxParEvenement();
}
