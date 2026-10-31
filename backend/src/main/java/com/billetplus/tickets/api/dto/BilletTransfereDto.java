package com.billetplus.tickets.api.dto;

import com.billetplus.tickets.application.usecase.ConsulterBilletTransfereUseCase.BilletTransfere;

import java.util.UUID;

/** Billet du destinataire d'un transfert. {@code qr} est null s'il n'est plus valable. */
public record BilletTransfereDto(UUID id, UUID eventId, String categorie, String statut, String qr) {

    public static BilletTransfereDto from(BilletTransfere billet) {
        return new BilletTransfereDto(billet.id(), billet.eventId(), billet.categorie(), billet.statut(),
            billet.qr().orElse(null));
    }
}
