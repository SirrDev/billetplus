package com.billetplus.tickets.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

/**
 * Levée quand une réservation temporaire ne tient plus dans les places
 * disponibles (vendues + déjà réservées par d'autres commandes). Traduite en
 * HTTP 409.
 *
 * Message volontairement générique : ni identifiant, ni nombre de places
 * restantes (un acheteur ne doit pas pouvoir sonder le stock d'un organisateur).
 */
public class PlacesIndisponiblesException extends BusinessConflictException {

    public PlacesIndisponiblesException() {
        super("Plus assez de places disponibles pour cette catégorie");
    }
}
