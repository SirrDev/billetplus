package com.billetplus.tickets.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

import java.util.UUID;

/**
 * Levée quand des billets ont déjà été émis pour une commande et une catégorie
 * données. Empêche qu'une confirmation de paiement rejouée consomme le quota
 * et émette des billets une seconde fois. Traduite en HTTP 409.
 */
public class BilletsDejaEmisException extends BusinessConflictException {

    public BilletsDejaEmisException(UUID orderId, UUID categoryId) {
        super("Des billets de la catégorie %s ont déjà été émis pour la commande %s"
            .formatted(categoryId, orderId));
    }
}
