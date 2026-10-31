package com.billetplus.tickets.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

import java.util.UUID;

/**
 * Levée quand on tente de transférer un billet déjà transféré (ADR 0008) : le
 * transfert est définitif. Conflit métier (409).
 */
public class BilletDejaTransfereException extends BusinessConflictException {

    private final UUID ticketId;

    public BilletDejaTransfereException(UUID ticketId) {
        super("Ce billet a déjà été transféré");
        this.ticketId = ticketId;
    }

    public UUID getTicketId() {
        return ticketId;
    }
}
