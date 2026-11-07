package com.billetplus.events.infrastructure.evenements;

import com.billetplus.events.application.EventService;
import com.billetplus.shared.evenements.CompteSuspendu;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adaptateur entrant du module events : à la suspension d'un compte organisateur, publiée par
 * le module auth (ADR 0012), tous ses événements encore actifs sont suspendus (vente fermée,
 * billets annulés, commandes payées listées à rembourser).
 *
 * {@code @EventListener} synchrone : même transaction que la suspension du compte, qui
 * réussit ou échoue en entier.
 */
@Component
class CompteSuspenduListener {

    private final EventService eventService;

    CompteSuspenduListener(EventService eventService) {
        this.eventService = eventService;
    }

    @EventListener
    void surSuspension(CompteSuspendu evenement) {
        eventService.suspendreTousLesEvenementsDe(evenement.organisateurId());
    }
}
