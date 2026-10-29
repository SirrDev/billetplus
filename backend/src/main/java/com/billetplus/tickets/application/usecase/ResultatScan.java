package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Issue du contrôle d'un billet à l'entrée (ValidateTicketScanUseCase).
 *
 * Toutes ces issues sont des situations normales à l'entrée, pas des erreurs :
 * elles sont renvoyées, jamais levées. Les quatre premières supposent une
 * signature authentique (que seul le backend peut produire) : les détailler
 * n'aide pas à fabriquer un faux QR. {@link Issue#INVALIDE}, elle, ne porte
 * aucune donnée et reste identique quelle qu'en soit la cause.
 *
 * @param ticketId      billet concerné ; absent pour INVALIDE
 * @param premierScanLe heure du premier passage ; présente pour DEJA_SCANNE seulement
 * @param statut        statut du billet ; présent pour NON_VALIDE seulement
 */
public record ResultatScan(Issue issue, UUID ticketId, Instant premierScanLe, TicketStatus statut) {

    public enum Issue {
        /** Entrée autorisée : le billet est désormais SCANNE. */
        ACCEPTE,
        /** Le billet a déjà servi : à montrer avec l'heure du premier passage (QR copié ?). */
        DEJA_SCANNE,
        /** Billet annulé ou remboursé. */
        NON_VALIDE,
        /** Billet authentique, mais pour un autre événement que celui contrôlé. */
        AUTRE_EVENEMENT,
        /** QR absent, mal formé, falsifié, billet inconnu ou incohérent : aucun détail. */
        INVALIDE
    }

    private static final ResultatScan INVALIDE = new ResultatScan(Issue.INVALIDE, null, null, null);

    public static ResultatScan accepte(UUID ticketId) {
        return new ResultatScan(Issue.ACCEPTE, ticketId, null, null);
    }

    public static ResultatScan dejaScanne(UUID ticketId, Instant premierScanLe) {
        return new ResultatScan(Issue.DEJA_SCANNE, ticketId, premierScanLe, null);
    }

    public static ResultatScan nonValide(UUID ticketId, TicketStatus statut) {
        return new ResultatScan(Issue.NON_VALIDE, ticketId, null, statut);
    }

    public static ResultatScan autreEvenement(UUID ticketId) {
        return new ResultatScan(Issue.AUTRE_EVENEMENT, ticketId, null, null);
    }

    public static ResultatScan invalide() {
        return INVALIDE;
    }
}
