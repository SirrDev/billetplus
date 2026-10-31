package com.billetplus.tickets.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corps de POST /api/billets/{id}/consultation : mobile du destinataire, au format
 * international (+2217XXXXXXXX), que la PWA normalise avant l'envoi. Dans le corps et
 * non dans l'URL, qui finit dans les journaux.
 */
public record ConsulterBilletTransfereRequest(
    @NotBlank
    @Pattern(regexp = "\\+2217\\d{8}", message = "Numéro de mobile sénégalais invalide")
    String telephone
) {}
