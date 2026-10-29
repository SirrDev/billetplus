package com.billetplus.tickets.api.dto;

import com.billetplus.tickets.domain.TicketCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Catégorie de billets telle que la voit un client de l'API.
 *
 * Un acheteur voit seulement si la catégorie est disponible, jamais le stock
 * (relecture sécurité de la PR #20 : un concurrent ne doit pas pouvoir sonder
 * les ventes d'un organisateur). L'organisateur voit en plus les quotas.
 * Les champs absents ne sont pas sérialisés.
 *
 * « disponible » ne tient pas compte des réservations temporaires en cours
 * (Redis) : une catégorie peut s'afficher disponible et refuser la réservation
 * (409), jamais l'inverse.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketCategoryDto(
    UUID id,
    String nom,
    long prixFcfa,
    boolean disponible,
    Integer quotaTotal,
    Integer quotaVendu
) {
    public static TicketCategoryDto pourUnAcheteur(TicketCategory category) {
        return new TicketCategoryDto(category.getId(), category.getNom(), prix(category),
            category.quotaRestant() > 0, null, null);
    }

    public static TicketCategoryDto pourLOrganisateur(TicketCategory category) {
        return new TicketCategoryDto(category.getId(), category.getNom(), prix(category),
            category.quotaRestant() > 0, category.getQuotaTotal(), category.getQuotaVendu());
    }

    private static long prix(TicketCategory category) {
        return category.getPrix().valeur().longValueExact();
    }
}
