package com.billetplus.tickets.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

import java.util.UUID;

/**
 * Levée quand une réservation dépasserait le quota disponible d'une catégorie
 * de billet. Voir cahier des charges — anti-overselling, et ADR 0003 pour la
 * stratégie de verrouillage associée côté infrastructure. Traduite en HTTP 409.
 */
public class QuotaDepasseException extends BusinessConflictException {

    public QuotaDepasseException(UUID categoryId, int quantiteDemandee, int quotaRestant) {
        super("Quota dépassé pour la catégorie %s : %d demandés, %d restants"
            .formatted(categoryId, quantiteDemandee, quotaRestant));
    }
}
