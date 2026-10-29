package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.tickets.domain.Ticket;

/**
 * Conversion domaine ↔ entité JPA pour les billets. Même découpage que
 * {@link TicketCategoryMapper} : seuls status et scanneLe sont mutables dans
 * le domaine, donc seuls champs recopiés par {@link #copierDans}.
 */
final class TicketMapper {

    private TicketMapper() {
    }

    static Ticket versDomaine(TicketJpaEntity entity) {
        return Ticket.reconstituer(
            entity.getId(),
            entity.getEventId(),
            entity.getCategoryId(),
            entity.getOrderId(),
            entity.getStatus(),
            entity.getEmisLe(),
            entity.getScanneLe()
        );
    }

    static TicketJpaEntity versNouvelleEntite(Ticket ticket) {
        return new TicketJpaEntity(
            ticket.getId(),
            ticket.getEventId(),
            ticket.getCategoryId(),
            ticket.getOrderId(),
            ticket.getStatus(),
            ticket.getEmisLe(),
            ticket.getScanneLe()
        );
    }

    static void copierDans(Ticket ticket, TicketJpaEntity entity) {
        if (!ticket.getId().equals(entity.getId())) {
            throw new IllegalStateException(
                "Identifiants différents : domaine %s, entité %s".formatted(ticket.getId(), entity.getId()));
        }
        entity.setStatus(ticket.getStatus());
        entity.setScanneLe(ticket.getScanneLe());
    }
}
