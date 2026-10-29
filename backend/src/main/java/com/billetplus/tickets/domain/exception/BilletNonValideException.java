package com.billetplus.tickets.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.domain.TicketStatus;

import java.util.UUID;

/**
 * Levée quand on tente de scanner un billet qui n'est plus valable pour
 * l'entrée : annulé ou remboursé. Conflit métier (409), pas une panne (500) :
 * le statut est exposé pour que l'agent puisse l'expliquer au client.
 *
 * Message volontairement générique (voir TicketAlreadyScannedException) :
 * l'identifiant et le statut restent accessibles par les accesseurs.
 */
public class BilletNonValideException extends BusinessConflictException {

    private final UUID ticketId;
    private final TicketStatus status;

    public BilletNonValideException(UUID ticketId, TicketStatus status) {
        super("Billet non valable pour l'entrée");
        this.ticketId = ticketId;
        this.status = status;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public TicketStatus getStatus() {
        return status;
    }
}
