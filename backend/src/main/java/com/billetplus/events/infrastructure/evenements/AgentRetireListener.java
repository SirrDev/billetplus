package com.billetplus.events.infrastructure.evenements;

import com.billetplus.events.application.AffectationAgentService;
import com.billetplus.shared.evenements.AgentRetire;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adaptateur entrant du module events : à la réception du retrait d'un agent, publié par le
 * module auth, supprime ses affectations (ADR 0005). Son jeton d'accès reste valide jusqu'à
 * 15 minutes, mais la route de scan vérifie l'affectation : il ne contrôle plus rien.
 *
 * {@code @EventListener} synchrone : même transaction que le retrait, qui réussit ou échoue en entier.
 */
@Component
class AgentRetireListener {

    private final AffectationAgentService affectationAgentService;

    AgentRetireListener(AffectationAgentService affectationAgentService) {
        this.affectationAgentService = affectationAgentService;
    }

    @EventListener
    void surRetrait(AgentRetire evenement) {
        affectationAgentService.retirerDeTousLesEvenements(evenement.agentId());
    }
}
