package com.billetplus.orders.api.dto;

import com.billetplus.orders.domain.Order;

import java.time.Instant;
import java.util.UUID;

/**
 * Commande à rembourser, vue par l'administrateur : avec le téléphone de
 * l'acheteur et la référence du paiement, nécessaires au remboursement manuel.
 */
public record CommandeARembourserDto(
    UUID id,
    UUID eventId,
    String telephone,
    long montantFcfa,
    String motif,
    Instant payeeLe,
    String moyenPaiement,
    String referencePaiement
) {
    public static CommandeARembourserDto from(Order commande) {
        return new CommandeARembourserDto(
            commande.getId(), commande.getEventId(), commande.getTelephone().valeur(),
            commande.montantTotal().valeur().longValueExact(), commande.getMotifSansBillet().name(),
            commande.getPayeeLe(),
            commande.getPaiement() == null ? null : commande.getPaiement().moyen().name(),
            commande.getPaiement() == null ? null : commande.getPaiement().reference()
        );
    }
}
