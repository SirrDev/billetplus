package com.billetplus.orders.api.dto;

import com.billetplus.orders.domain.MoyenPaiement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de POST /api/credit/recharges (ADR 0010). Les bornes du montant viennent de la
 * configuration et sont vérifiées par le domaine (400). L'organisateur vient du jeton.
 *
 * @param telephone numéro qui paie (Wave ou Orange Money), au format accepté pour une commande
 */
public record RechargerCreditRequest(
    @NotNull Long montantFcfa,
    @NotNull MoyenPaiement moyen,
    @NotBlank @Size(max = 20) String telephone
) {
}
