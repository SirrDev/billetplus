package com.billetplus.tickets.api.publique;

import java.util.UUID;

/**
 * Port public du module tickets (ADR 0005, ADR 0008) : transfert d'un billet d'une
 * commande vers un autre mobile, demandé par le module orders. Implémenté à
 * l'intérieur du module tickets.
 *
 * Contrat de l'appelant (orders) : n'appeler qu'après avoir authentifié l'acheteur
 * de la commande (jeton d'accès, ADR 0006) et vérifié qu'elle est payée. Le module
 * tickets vérifie que le billet appartient à la commande, qu'il est encore valable,
 * qu'il n'a jamais été transféré et que l'événement est en vente.
 */
public interface TransfertBillets {

    /**
     * Transfère le billet et renvoie le jeton du destinataire, en clair, à remettre
     * une seule fois à l'expéditeur : il n'est stocké que sous forme d'empreinte.
     * Jeton porteur : ne jamais le journaliser.
     *
     * @param telephoneE164 mobile du destinataire, déjà normalisé (+2217XXXXXXXX)
     * @throws java.util.NoSuchElementException billet inconnu ou d'une autre commande (404)
     * @throws com.billetplus.shared.exception.BusinessConflictException billet déjà transféré,
     *         plus valable, ou événement hors vente (409)
     */
    String transferer(UUID orderId, UUID ticketId, String telephoneE164);
}
