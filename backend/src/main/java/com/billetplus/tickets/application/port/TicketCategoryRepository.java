package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.TicketCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — persistance des catégories de billets.
 * L'implémentation JPA (entité séparée + mapper + adaptateur) vit dans
 * infrastructure/persistence/ (voir feature/backend-tickets-jpa-mapper).
 *
 * Verrouillage optimiste : l'adaptateur s'appuie sur une colonne version
 * absente du domaine. La protection contre la survente n'est effective que si
 * {@link #findById(UUID)}, la modification du domaine et {@link #save(TicketCategory)}
 * s'exécutent dans la MÊME transaction : c'est à l'appelant (cas d'usage
 * annoté @Transactional) de la garantir. L'adaptateur JPA refuse un save()
 * appelé hors transaction (propagation MANDATORY).
 */
public interface TicketCategoryRepository {

    Optional<TicketCategory> findById(UUID id);

    /** Catégories d'un événement, de la moins chère à la plus chère. */
    List<TicketCategory> findByEventId(UUID eventId);

    TicketCategory save(TicketCategory category);
}
