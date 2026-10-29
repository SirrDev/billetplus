package com.billetplus.orders.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Corps de POST /api/orders (achat sans compte, ADR 0006). Ni orderId, ni prix,
 * ni statut : tout cela est décidé par le serveur.
 *
 * telephone : 30 caractères au plus à la saisie (espaces et indicatif compris),
 * normalisé ensuite en +2217XXXXXXXX (VARCHAR(13) en base).
 */
public record CreateOrderRequest(
    @NotNull UUID eventId,
    @NotBlank @Size(max = 30) String telephone,
    @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid LigneCommandeRequest> lignes
) {
}
