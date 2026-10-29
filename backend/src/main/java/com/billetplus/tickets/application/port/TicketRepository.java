package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.Ticket;

/**
 * Port (niveau 3, voir ADR 0002) — persistance des billets. L'implémentation
 * JPA vit dans infrastructure/persistence/ (voir feature/backend-tickets-jpa-mapper).
 */
public interface TicketRepository {

    Ticket save(Ticket ticket);
}
