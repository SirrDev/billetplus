package com.billetplus.orders.api.dto;

import com.billetplus.tickets.api.publique.BilletVendu;

import java.util.UUID;

/** Un billet de l'acheteur. {@code qr} est null pour un billet scanné, annulé ou remboursé. */
public record BilletDto(UUID id, UUID categoryId, String categorie, String statut, String qr) {

    public static BilletDto from(BilletVendu billet) {
        return new BilletDto(billet.id(), billet.categoryId(), billet.categoryNom(), billet.statut(),
            billet.qr().orElse(null));
    }
}
