package com.billetplus.events.api.publique;

import java.util.UUID;

/**
 * Port public du module events (ADR 0005) : le seul point d'entrée par lequel
 * un autre module peut lire l'état d'un événement. Implémenté à l'intérieur
 * du module events ; les autres modules ne connaissent que cette interface.
 *
 * Uniquement des booléens : la règle ArchUnit ports_publics_ne_contiennent_que_des_interfaces
 * interdit tout autre type (énumération, record) dans api.publique.
 */
public interface StatutEvenements {

    /**
     * Vrai si l'événement existe et est publié (ni brouillon, ni annulé, ni
     * terminé) : c'est la seule situation où l'on peut réserver des places ou
     * émettre des billets.
     */
    boolean estOuvertALaVente(UUID eventId);

    /**
     * Vrai si l'événement existe et que {@code userId} en est l'organisateur.
     * Faux pour un identifiant absent ou inconnu.
     */
    boolean estOrganisateur(UUID eventId, UUID userId);

    /**
     * Vrai si l'événement existe et accepte encore la configuration de sa
     * billetterie (catégories) : brouillon ou publié. Faux s'il est annulé ou terminé.
     */
    boolean accepteDesCategories(UUID eventId);

    /**
     * Vrai si {@code agentId} est affecté au contrôle d'entrée de l'événement :
     * condition pour scanner ses billets (route de scan, contrat de
     * ValidateTicketScanUseCase). Faux pour un identifiant absent ou inconnu.
     */
    boolean estAgentAffecte(UUID eventId, UUID agentId);
}
