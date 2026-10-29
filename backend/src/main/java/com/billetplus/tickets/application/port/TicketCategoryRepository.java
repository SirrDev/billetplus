package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.TicketCategory;

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
 * annoté @Transactional) de la garantir.
 */
public interface TicketCategoryRepository {

    Optional<TicketCategory> findById(UUID id);

    TicketCategory save(TicketCategory category);
}
