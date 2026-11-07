package com.billetplus.orders.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de POST /api/admin/reversements. Limites alignées sur V16 (reference VARCHAR(100),
 * motif VARCHAR(255)) et sur Reversement.MONTANT_MAX_FCFA. Aucun administrateur dans le
 * corps : il vient du jeton.
 */
public record EnregistrerReversementRequest(
    @NotNull
    UUID eventId,

    @NotNull
    @Min(value = -1_000_000_000_000L, message = "Montant hors limites")
    @Max(value = 1_000_000_000_000L, message = "Montant hors limites")
    Long montantFcfa,

    @NotBlank
    @Size(max = 100, message = "La référence ne doit pas dépasser 100 caractères")
    String reference,

    @Size(max = 255, message = "Le motif ne doit pas dépasser 255 caractères")
    String motif,

    @NotNull
    LocalDate reverseLe,

    /** Virement sans billet scanné (entrée sans contrôle, ADR 0012) : motif obligatoire, sinon null. */
    @Size(max = 255, message = "Le motif de la dérogation ne doit pas dépasser 255 caractères")
    String derogationScans
) {}
