package com.billetplus.orders.api.dto;

import com.billetplus.orders.domain.RecettesEvenement;

/**
 * Recettes d'un événement (ADR 0009). En FCFA entiers. {@code resteAReverserFcfa} est
 * négatif si l'organisateur a reçu plus que son net (événement annulé après un reversement).
 */
public record RecettesDto(long commandes, long brutFcfa, long commissionFcfa, long netFcfa, long reverseFcfa,
                          long resteAReverserFcfa) {

    public static RecettesDto from(RecettesEvenement r) {
        return new RecettesDto(r.commandes(), r.brut().valeur().longValueExact(),
            r.commission().valeur().longValueExact(), r.net().valeur().longValueExact(), r.reverseFcfa(),
            r.resteAReverserFcfa());
    }
}
