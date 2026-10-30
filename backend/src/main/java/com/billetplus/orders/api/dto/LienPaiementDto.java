package com.billetplus.orders.api.dto;

import com.billetplus.orders.application.usecase.OuvrirPaiementUseCase;

import java.time.Instant;

/** Lien de paiement à ouvrir par l'acheteur, et échéance de sa commande. */
public record LienPaiementDto(String moyen, String urlPaiement, Instant expireLe) {

    public static LienPaiementDto from(OuvrirPaiementUseCase.LienPaiement lien) {
        return new LienPaiementDto(lien.moyen().name(), lien.url(), lien.expireLe());
    }
}
