package com.billetplus.orders.api.dto;

import java.util.UUID;

/**
 * Réponse au transfert : le jeton du destinataire, en clair, remis UNE SEULE FOIS
 * (seule son empreinte est stockée). La PWA en fait un lien à partager.
 */
public record BilletTransfereResponse(UUID ticketId, String jeton) {
}
