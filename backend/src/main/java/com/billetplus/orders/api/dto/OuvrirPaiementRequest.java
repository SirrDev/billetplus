package com.billetplus.orders.api.dto;

import com.billetplus.orders.domain.MoyenPaiement;
import jakarta.validation.constraints.NotNull;

/** Corps de POST /api/orders/{id}/paiement : WAVE ou ORANGE_MONEY. Le montant est celui de la commande. */
public record OuvrirPaiementRequest(@NotNull MoyenPaiement moyen) {
}
