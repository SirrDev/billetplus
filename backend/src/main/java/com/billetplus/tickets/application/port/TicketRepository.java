package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.Ticket;

import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — persistance des billets. L'implémentation
 * JPA vit dans infrastructure/persistence/ (voir feature/backend-tickets-jpa-mapper).
 */
public interface TicketRepository {

    Ticket save(Ticket ticket);

    /**
     * Vrai si des billets de cette catégorie ont déjà été émis pour cette
     * commande. Sert de garde contre une double émission (confirmation de
     * paiement rejouée, par exemple un webhook Wave reçu deux fois).
     */
    boolean existsByOrderIdAndCategoryId(UUID orderId, UUID categoryId);
}
