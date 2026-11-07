package com.billetplus.tickets.api.publique;

import java.util.UUID;

/**
 * Port public du module tickets (ADR 0005) : ce que le contrôle d'entrée a constaté. Le module
 * orders n'autorise un reversement que si au moins un billet a été scanné (ADR 0012) : un faux
 * événement n'en a aucun.
 */
public interface ControleEntrees {

    /** Billets de l'événement scannés à l'entrée (statut SCANNE) ; zéro pour un identifiant inconnu. */
    long billetsScannes(UUID eventId);
}
