package com.billetplus.orders.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de POST /api/orders/{id}/billets/{ticketId}/transfert : mobile du destinataire,
 * dans toute écriture acceptée par NumeroTelephone (normalisé ensuite, 400 sinon).
 * Borné comme CreateOrderRequest.telephone.
 */
public record TransfererBilletRequest(
    @NotBlank
    @Size(max = 30, message = "Le numéro de téléphone ne doit pas dépasser 30 caractères")
    String telephone
) {}
