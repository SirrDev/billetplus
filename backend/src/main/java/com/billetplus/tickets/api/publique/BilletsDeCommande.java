package com.billetplus.tickets.api.publique;

import java.util.List;
import java.util.UUID;

/**
 * Port public du module tickets (ADR 0005) : les billets d'une commande, avec
 * leur QR recalculé. Les QR ne sont jamais stockés : la signature Ed25519 est
 * déterministe, le même billet donne toujours le même QR.
 *
 * Contrat de l'appelant (orders) : n'appeler qu'après avoir authentifié
 * l'acheteur de la commande (jeton d'accès, ADR 0006). Le module tickets ne
 * contrôle aucune propriété.
 */
public interface BilletsDeCommande {

    /** Billets de la commande, triés par catégorie puis identifiant. Vide si aucun. */
    List<BilletVendu> billetsDe(UUID orderId);
}
