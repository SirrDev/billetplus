package com.billetplus.auth.infrastructure.evenements;

import com.billetplus.auth.application.VerificationService;
import com.billetplus.shared.evenements.ReversementEnregistre;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adaptateur entrant du module auth : un reversement enregistré par le module orders repousse
 * la fin de conservation des pièces de l'organisateur (5 ans après le dernier, ADR 0012).
 * {@code @EventListener} synchrone : même transaction que l'enregistrement du reversement.
 */
@Component
class ReversementEnregistreListener {

    private final VerificationService verificationService;

    ReversementEnregistreListener(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @EventListener
    void surReversement(ReversementEnregistre evenement) {
        verificationService.reversementEnregistre(evenement.organisateurId(), evenement.enregistreLe());
    }
}
