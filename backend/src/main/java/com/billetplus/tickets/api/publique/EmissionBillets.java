package com.billetplus.tickets.api.publique;

import java.util.UUID;

/**
 * Port public du module tickets (ADR 0005) : émission des billets d'une commande
 * payée, par le module orders. Implémenté à l'intérieur du module tickets
 * (délègue à IssueTicketUseCase).
 *
 * Rejoint la transaction de l'appelant : une exception la condamne. L'appelant ne
 * l'attrape donc jamais À L'INTÉRIEUR de sa transaction (UnexpectedRollbackException),
 * mais après son annulation. Contrat complet : voir IssueTicketUseCase.
 */
public interface EmissionBillets {

    /**
     * Émet {@code quantite} billets de la catégorie pour la commande.
     *
     * @throws com.billetplus.shared.exception.BusinessConflictException vente fermée, quota
     *         épuisé ou billets déjà émis pour ce couple (commande, catégorie)
     * @throws java.util.NoSuchElementException catégorie inconnue
     */
    void emettre(UUID categoryId, UUID orderId, int quantite);
}
