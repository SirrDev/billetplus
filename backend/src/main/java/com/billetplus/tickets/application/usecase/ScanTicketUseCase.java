package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — un agent scanne un billet à l'entrée.
 * Il fait respecter le contrat de {@link ValidateTicketScanUseCase} :
 *
 * 1. autorisation : l'utilisateur du jeton doit être affecté à l'événement
 *    contrôlé, ou en être l'organisateur (port public du module events, ADR 0005),
 *    sinon 403 avant toute lecture du QR ;
 * 2. double scan simultané : le conflit de version levé à la validation de la
 *    transaction est rejoué UNE fois. Le second essai, dans une nouvelle
 *    transaction, lit le billet déjà scanné et renvoie DEJA_SCANNE : l'agent ne
 *    voit jamais « erreur technique, réessayez ».
 *
 * Volontairement sans @Transactional : chaque appel à ValidateTicketScanUseCase
 * ouvre sa propre transaction, ce qui rend le rejeu possible (un rejeu dans une
 * transaction déjà marquée à annuler échouerait).
 */
@Service
public class ScanTicketUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ScanTicketUseCase.class);

    private final ValidateTicketScanUseCase validateScan;
    private final StatutEvenements statutEvenements;

    public ScanTicketUseCase(ValidateTicketScanUseCase validateScan, StatutEvenements statutEvenements) {
        this.validateScan = validateScan;
        this.statutEvenements = statutEvenements;
    }

    /**
     * @param eventIdDuControle événement dont l'utilisateur dit contrôler l'entrée
     * @param utilisateurId     l'utilisateur authentifié (jeton)
     * @throws AccessDeniedException si l'utilisateur n'est ni affecté à cet événement ni son organisateur
     */
    public ResultatScan execute(String qrContent, UUID eventIdDuControle, UUID utilisateurId) {
        boolean autorise = statutEvenements.estAgentAffecte(eventIdDuControle, utilisateurId)
            || statutEvenements.estOrganisateur(eventIdDuControle, utilisateurId);
        if (!autorise) {
            throw new AccessDeniedException("Vous ne contrôlez pas l'entrée de cet événement");
        }
        try {
            return validateScan.execute(qrContent, eventIdDuControle);
        } catch (OptimisticLockingFailureException conflit) {
            // Un autre agent vient de scanner le même billet : le rejeu le lit SCANNE.
            LOG.info("Scan simultané du même billet à l'événement {} : rejeu", eventIdDuControle);
            return validateScan.execute(qrContent, eventIdDuControle);
        }
    }
}
