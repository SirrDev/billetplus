package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketStatus;

import java.util.List;
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

    /** Billets d'un événement ayant un statut donné (par exemple tous les EMIS, à annuler). */
    List<Ticket> findByEventIdAndStatus(UUID eventId, TicketStatus status);

    /** Billets d'une commande, quel que soit leur statut. */
    List<Ticket> findByOrderId(UUID orderId);

    /** Nombre de billets d'une catégorie dans un statut (une ligne par couple présent). */
    record CompteBillets(UUID categoryId, TicketStatus statut, long nombre) {
    }

    /** Billets d'un événement comptés par catégorie et par statut, en une seule requête. */
    List<CompteBillets> compterParCategorieEtStatut(UUID eventId);

    Ticket save(Ticket ticket);
}
