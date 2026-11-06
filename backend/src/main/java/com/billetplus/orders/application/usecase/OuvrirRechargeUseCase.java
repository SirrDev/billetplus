package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.orders.application.port.RechargeRepository;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Paiement;
import com.billetplus.orders.domain.RechargeCredit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — l'organisateur recharge son crédit prépayé
 * (ADR 0010) : la recharge est enregistrée en attente, puis le paiement est ouvert
 * chez la passerelle HORS transaction (ADR 0007), puis enregistré sur la recharge.
 * Le solde n'est crédité que par le webhook (ConfirmerRechargeUseCase).
 */
@Service
public class OuvrirRechargeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(OuvrirRechargeUseCase.class);

    /** Ce que l'organisateur reçoit : la recharge et le lien de paiement. */
    public record LienRecharge(UUID rechargeId, MoyenPaiement moyen, String url) {
    }

    private final RechargeRepository rechargeRepository;
    private final PasserellePaiement passerelle;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final long montantMin;
    private final long montantMax;

    public OuvrirRechargeUseCase(RechargeRepository rechargeRepository, PasserellePaiement passerelle,
                                 PlatformTransactionManager transactionManager, Clock clock,
                                 @Value("${billetplus.credit.recharge-min:1000}") long montantMin,
                                 @Value("${billetplus.credit.recharge-max:1000000}") long montantMax) {
        if (montantMin < 1 || montantMax < montantMin) {
            throw new IllegalStateException("billetplus.credit.recharge-min et recharge-max sont incohérents");
        }
        this.rechargeRepository = rechargeRepository;
        this.passerelle = passerelle;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.montantMin = montantMin;
        this.montantMax = montantMax;
    }

    /**
     * @param organisateurId vient du jeton, jamais de la requête
     * @throws IllegalArgumentException (400) montant hors bornes, téléphone ou moyen invalide
     * @throws com.billetplus.orders.domain.exception.PaiementRefuseException (409) refus de la passerelle
     * @throws com.billetplus.shared.exception.ServiceExterneIndisponibleException (503) passerelle en panne
     */
    public LienRecharge execute(UUID organisateurId, long montantFcfa, MoyenPaiement moyen, String telephoneSaisi) {
        if (moyen == null) {
            throw new IllegalArgumentException("Le moyen de paiement est obligatoire");
        }
        NumeroTelephone telephone = NumeroTelephone.depuis(telephoneSaisi);
        RechargeCredit nouvelle = RechargeCredit.creer(organisateurId, montantFcfa, telephone, montantMin, montantMax,
            Instant.now(clock));
        RechargeCredit enregistree = transaction.execute(statut -> rechargeRepository.save(nouvelle));

        PasserellePaiement.PaiementCree cree = passerelle.ouvrir(new PasserellePaiement.DemandePaiement(
            enregistree.getId(), moyen, enregistree.getMontant(), telephone, "Billet+ : recharge du credit",
            PasserellePaiement.Retour.RECHARGE));

        try {
            transaction.executeWithoutResult(statut -> {
                RechargeCredit relue = rechargeRepository.findById(enregistree.getId())
                    .orElseThrow(() -> new NoSuchElementException("Recharge introuvable"));
                relue.ouvrirPaiement(new Paiement(moyen, cree.reference(), cree.transactionId(), cree.url(),
                    Instant.now(clock)));
                rechargeRepository.save(relue);
            });
        } catch (RuntimeException e) {
            LOG.warn("Paiement ouvert chez la passerelle mais non enregistré (recharge {}, référence {}) : "
                + "il restera sans suite", enregistree.getId(), cree.reference(), e);
            throw e;
        }
        return new LienRecharge(enregistree.getId(), moyen, cree.url());
    }
}
