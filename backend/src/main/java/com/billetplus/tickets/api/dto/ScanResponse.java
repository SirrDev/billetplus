package com.billetplus.tickets.api.dto;

import com.billetplus.tickets.application.usecase.ResultatScan;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse du contrôle d'entrée, montrée à l'agent. Reprend le ResultatScan,
 * sans aucune donnée de la commande ni de l'acheteur (contrat de
 * ValidateTicketScanUseCase). Les champs absents ne sont pas sérialisés : une
 * issue INVALIDE ne porte que son nom.
 *
 * L'écran du scanner traite toute issue autre qu'ACCEPTE comme un refus d'entrée.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScanResponse(String issue, UUID ticketId, Instant premierScanLe, String statut) {

    public static ScanResponse from(ResultatScan resultat) {
        return new ScanResponse(
            resultat.issue().name(),
            resultat.ticketId(),
            resultat.premierScanLe(),
            resultat.statut() == null ? null : resultat.statut().name());
    }
}
