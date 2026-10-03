package com.billetplus.tickets.domain.exception;

import java.time.Instant;
import java.util.UUID;

/**
 * Levée lors d'une tentative de double scan d'un billet déjà utilisé.
 * Voir cahier des charges — détection de double entrée en temps réel.
 */
public class TicketAlreadyScannedException extends RuntimeException {

    public TicketAlreadyScannedException(UUID ticketId, Instant premierScanLe) {
        super("Le billet %s a déjà été scanné le %s".formatted(ticketId, premierScanLe));
    }
}
