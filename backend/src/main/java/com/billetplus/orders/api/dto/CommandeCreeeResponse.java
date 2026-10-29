package com.billetplus.orders.api.dto;

import com.billetplus.orders.domain.Order;

/**
 * Réponse de création : la SEULE réponse qui contienne le jeton d'accès en clair
 * (ADR 0006). La PWA doit le conserver : aucune route ne le renvoie ensuite.
 */
public record CommandeCreeeResponse(CommandeDto commande, String jetonAcces) {

    public static CommandeCreeeResponse from(Order.CommandeCreee creee) {
        return new CommandeCreeeResponse(CommandeDto.from(creee.commande()), creee.jeton().enClair());
    }
}
