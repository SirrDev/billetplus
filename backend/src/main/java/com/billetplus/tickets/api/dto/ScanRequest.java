package com.billetplus.tickets.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contenu brut lu dans le QR code. Borné comme ValidateTicketScanUseCase.TAILLE_MAX_QR
 * (1024) : un contenu plus long est refusé en 400 avant toute cryptographie.
 * Volontairement sans eventId ni agent : l'événement vient de l'URL (accepté
 * seulement si l'agent y est affecté), l'agent du jeton.
 */
public record ScanRequest(
    @NotBlank
    @Size(max = 1024, message = "Le contenu du QR ne doit pas dépasser 1024 caractères")
    String qrContent
) {}
