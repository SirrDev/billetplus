package com.billetplus.orders.api.dto;

import com.billetplus.orders.application.usecase.ListerCommandesARembourserUseCase;
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
    public static CommandeARembourserDto from(ListerCommandesARembourserUseCase.ARembourser aRembourser) {
        Order commande = aRembourser.commande();
        return new CommandeARembourserDto(
            commande.getId(), commande.getEventId(), commande.getTelephone().valeur(),
            commande.montantTotal().valeur().longValueExact(), aRembourser.motif(),
            commande.getPayeeLe(),
            commande.getPaiement() == null ? null : commande.getPaiement().moyen().name(),
            commande.getPaiement() == null ? null : commande.getPaiement().reference()
        );
    }
}
