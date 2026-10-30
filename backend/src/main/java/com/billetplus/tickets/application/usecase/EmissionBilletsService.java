package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.api.publique.EmissionBillets;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implémentation du port public {@link EmissionBillets} (ADR 0005). Aucune règle
 * propre : orders passe par les mêmes contrôles que tout appelant d'IssueTicketUseCase.
 * Les QR codes signés ne sont pas renvoyés : ils se recalculent (export PDF).
 */
@Service
class EmissionBilletsService implements EmissionBillets {

    private final IssueTicketUseCase issueTicket;

    EmissionBilletsService(IssueTicketUseCase issueTicket) {
        this.issueTicket = issueTicket;
    }

    @Override
    public void emettre(UUID categoryId, UUID orderId, int quantite) {
        issueTicket.execute(categoryId, orderId, quantite);
    }
}
