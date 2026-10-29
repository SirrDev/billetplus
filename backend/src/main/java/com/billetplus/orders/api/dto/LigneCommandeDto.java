package com.billetplus.orders.api.dto;

import com.billetplus.orders.domain.OrderLine;

import java.util.UUID;

public record LigneCommandeDto(UUID categoryId, int quantite, long prixUnitaireFcfa, long sousTotalFcfa) {

    public static LigneCommandeDto from(OrderLine ligne) {
        return new LigneCommandeDto(ligne.categoryId(), ligne.quantite(),
            ligne.prixUnitaire().valeur().longValueExact(), ligne.sousTotal().valeur().longValueExact());
    }
}
