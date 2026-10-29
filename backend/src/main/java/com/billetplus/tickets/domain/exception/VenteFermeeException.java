package com.billetplus.tickets.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

/**
 * Levée quand on veut émettre des billets pour un événement qui n'est plus
 * ouvert à la vente (annulé, terminé, ou repassé hors vente) : par exemple un
 * paiement confirmé après l'annulation de l'événement. Traduite en HTTP 409.
 *
 * Pour l'appelant (module orders), c'est un cas « payé sans billet » : la
 * commande doit être remboursée. Message générique, sans identifiant.
 */
public class VenteFermeeException extends BusinessConflictException {

    public VenteFermeeException() {
        super("La vente est fermée pour cet événement");
    }
}
