package com.billetplus.auth.infrastructure;

import com.billetplus.auth.application.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Suppression quotidienne des pièces échues des organisateurs (ADR 0012) : 5 ans après le
 * dernier reversement, ou après le dépôt sans reversement. Journalise un nombre, jamais
 * une donnée personnelle.
 */
@Component
class PurgePiecesTache {

    private static final Logger LOG = LoggerFactory.getLogger(PurgePiecesTache.class);

    private final VerificationService verificationService;

    PurgePiecesTache(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Scheduled(cron = "${billetplus.verification.purge-cron:0 30 3 * * *}", zone = "UTC")
    void purger() {
        int supprimees = verificationService.purgerLesPiecesEchues();
        if (supprimees > 0) {
            LOG.info("Pièces d'organisateurs échues supprimées : {}", supprimees);
        }
    }
}
