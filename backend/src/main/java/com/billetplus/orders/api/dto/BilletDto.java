package com.billetplus.orders.api.dto;

import com.billetplus.tickets.api.publique.BilletVendu;

import java.util.UUID;

/**
 * Un billet de l'acheteur. {@code qr} est null pour un billet scanné, annulé,
 * remboursé ou transféré ; {@code transfereA} (mobile masqué) n'est présent que
 * pour un billet transféré (statut TRANSFERE, ADR 0008).
 */
public record BilletDto(UUID id, UUID categoryId, String categorie, String statut, String qr, String transfereA) {

    public static BilletDto from(BilletVendu billet) {
        return new BilletDto(billet.id(), billet.categoryId(), billet.categoryNom(), billet.statut(),
            billet.qr().orElse(null), billet.transfereA().orElse(null));
    }
}
