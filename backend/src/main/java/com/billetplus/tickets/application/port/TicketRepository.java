package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.Ticket;

import java.util.Optional;
import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — persistance des billets. L'implémentation
 * JPA vit dans infrastructure/persistence/ (voir feature/backend-tickets-jpa-mapper).
 *
 * Verrouillage optimiste : comme pour TicketCategoryRepository, la protection
 * (ici contre deux scans simultanés du même billet) n'est effective que si
 * {@link #findById(UUID)}, la modification du domaine et {@link #save(Ticket)}
 * s'exécutent dans la MÊME transaction, celle du cas d'usage.
 */
public interface TicketRepository {

    Optional<Ticket> findById(UUID id);

    Ticket save(Ticket ticket);
}
