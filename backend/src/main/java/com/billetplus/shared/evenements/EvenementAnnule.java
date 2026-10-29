package com.billetplus.shared.evenements;

import java.util.UUID;

/**
 * Événement applicatif : un événement (au sens billetterie) vient d'être
 * annulé. Publié par le module events, écouté par les modules qui en dépendent
 * (tickets annule alors ses billets). Voir ADR 0005.
 *
 * Simple donnée, sans logique (discipline de shared, ADR 0001). Les écouteurs
 * s'exécutent dans la transaction de l'annulation : s'ils échouent,
 * l'annulation est annulée avec eux.
 */
public record EvenementAnnule(UUID eventId) {
}
