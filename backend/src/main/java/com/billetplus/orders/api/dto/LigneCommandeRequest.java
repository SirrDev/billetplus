package com.billetplus.orders.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Une catégorie et un nombre de places. Jamais de prix : il est lu côté serveur. */
public record LigneCommandeRequest(
    @NotNull UUID categoryId,
    @Min(1) @Max(20) int quantite
) {
}
