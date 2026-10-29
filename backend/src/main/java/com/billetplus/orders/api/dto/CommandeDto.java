package com.billetplus.orders.api.dto;

import com.billetplus.orders.domain.Order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Commande telle que la voit son acheteur. Sans numéro de téléphone ni empreinte
 * du jeton : le lien peut être transféré, la réponse n'expose que l'achat.
 */
public record CommandeDto(
    UUID id,
    UUID eventId,
    String status,
    List<LigneCommandeDto> lignes,
    long montantTotalFcfa,
    Instant creeLe,
    Instant expireLe,
    Instant payeeLe
) {
    public static CommandeDto from(Order commande) {
        return new CommandeDto(
            commande.getId(), commande.getEventId(), commande.getStatus().name(),
            commande.getLignes().stream().map(LigneCommandeDto::from).toList(),
            commande.montantTotal().valeur().longValueExact(),
            commande.getCreeLe(), commande.getExpireLe(), commande.getPayeeLe()
        );
    }
}
