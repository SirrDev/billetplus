package com.billetplus.auth.api.dto;

import com.billetplus.auth.domain.VerificationOrganisateur;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de PUT /api/verification (ADR 0012). Tailles alignées sur V19 ; le numéro est
 * normalisé et contrôlé par le domaine (400).
 */
public record DeclarationVerificationRequest(
    @NotNull VerificationOrganisateur.TypeTitulaire typeTitulaire,
    @Size(max = 255) String nomStructure,
    @Size(max = 30) String ninea,
    @NotBlank @Size(max = 20) String numeroReversement,
    @NotBlank @Size(max = 255) String titulaireReversement
) {
}
