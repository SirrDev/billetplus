package com.billetplus.orders.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

/**
 * Changement de statut impossible depuis l'état courant de la commande (payer une
 * commande annulée, expirer une commande payée...). Traduite en HTTP 409.
 *
 * Message générique, sans identifiant ni statut : il peut être relayé à un
 * acheteur non authentifié (ADR 0006).
 */
public class TransitionCommandeInterditeException extends BusinessConflictException {

    public TransitionCommandeInterditeException(String message) {
        super(message);
    }
}
