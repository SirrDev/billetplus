package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.CreditRepository;
import com.billetplus.orders.application.port.NotificationPaiement;
import com.billetplus.orders.application.port.RechargeRepository;
import com.billetplus.orders.domain.RechargeCredit;
import com.billetplus.orders.domain.StatutRecharge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — traite une notification de paiement
 * AUTHENTIFIÉE dont la référence signée désigne une recharge du crédit (ADR 0010).
 * Appelé par ConfirmerPaiementUseCase quand aucune commande ne porte cette référence.
 *
 * Une transaction : relecture de la recharge (version), transition du domaine, crédit
 * du solde. Un webhook rejoué trouve la recharge déjà traitée : rien ne change. Deux
 * webhooks simultanés : le verrou de version, contrôlé à l'écriture de la recharge
 * (saveAndFlush, avant le crédit), n'en laisse passer qu'un ; l'autre lève une
 * OptimisticLockingFailureException, que le contrôleur rend en 503 à relancer. L'index
 * unique du journal interdit de toute façon un second crédit.
 */
@Service
public class ConfirmerRechargeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmerRechargeUseCase.class);

    public enum Resultat {
        /** Solde crédité. */
        CREDITEE,
        /** Montant signé différent : rien crédité, à régulariser à la main. */
        ANOMALIE,
        /** Paiement échoué ou expiré : recharge close sans crédit. */
        ECHOUEE,
        /** Rien à faire : déjà traitée, ou événement sans effet. */
        SANS_EFFET,
        /** Aucune recharge pour cette référence. */
        INCONNUE
    }

    private final RechargeRepository rechargeRepository;
    private final CreditRepository creditRepository;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public ConfirmerRechargeUseCase(RechargeRepository rechargeRepository, CreditRepository creditRepository,
                                    PlatformTransactionManager transactionManager, Clock clock) {
        this.rechargeRepository = rechargeRepository;
        this.creditRepository = creditRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Resultat traiter(NotificationPaiement notification) {
        Optional<RechargeCredit> trouvee = rechargeRepository.findByReferencePaiement(notification.reference());
        if (trouvee.isEmpty()) {
            return Resultat.INCONNUE;
        }
        if (notification.type() == NotificationPaiement.Type.AUTRE) {
            return Resultat.SANS_EFFET;
        }
        Resultat resultat = transaction.execute(statut -> {
            RechargeCredit recharge = rechargeRepository.findById(trouvee.get().getId()).orElseThrow();
            if (recharge.getStatus() != StatutRecharge.EN_ATTENTE) {
                if (notification.type() == NotificationPaiement.Type.PAYE
                    && recharge.getStatus() != StatutRecharge.PAYEE) {
                    // Webhooks dans le désordre (paiement après un échec) ou second paiement
                    // après une anomalie : de l'argent reçu sans crédit, jamais en silence.
                    LOG.error("Paiement reçu ({} FCFA, référence {}) pour la recharge {} déjà {} : rien crédité, "
                        + "à régulariser à la main", notification.montant(), notification.reference(),
                        recharge.getId(), recharge.getStatus());
                }
                return Resultat.SANS_EFFET;
            }
            Instant maintenant = Instant.now(clock);
            if (notification.type() == NotificationPaiement.Type.ECHOUE) {
                recharge.echouer(maintenant);
                rechargeRepository.save(recharge);
                return Resultat.ECHOUEE;
            }
            boolean aCrediter = recharge.confirmer(notification.montant(), maintenant);
            rechargeRepository.save(recharge);
            if (!aCrediter) {
                return Resultat.ANOMALIE;
            }
            creditRepository.crediterRecharge(recharge.getOrganisateurId(), recharge.getMontant(), recharge.getId(),
                maintenant);
            return Resultat.CREDITEE;
        });
        if (resultat == Resultat.ANOMALIE) {
            LOG.error("Recharge {} : montant payé ({} FCFA) différent du montant demandé ({}), rien crédité : "
                + "à régulariser à la main", trouvee.get().getId(), notification.montant(), trouvee.get().getMontant());
        }
        return resultat;
    }
}
