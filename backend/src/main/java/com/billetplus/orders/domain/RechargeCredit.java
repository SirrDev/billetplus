package com.billetplus.orders.domain;

import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.shared.Money;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — une recharge du crédit prépayé d'un
 * organisateur, payée par UnitechPay (ADR 0010). Un seul paiement par recharge,
 * comme pour une commande (ADR 0007). Le solde n'est crédité que par
 * {@link #confirmer}, une seule fois.
 */
public class RechargeCredit {

    private final UUID id;
    private final UUID organisateurId;
    private final Money montant;
    private final NumeroTelephone telephone;
    private final Instant creeLe;
    private StatutRecharge status;
    private Instant traiteeLe;
    private Long montantRecuFcfa;
    private Paiement paiement;

    /**
     * @param montantMinFcfa borne basse de la configuration (incluse)
     * @param montantMaxFcfa borne haute de la configuration (incluse)
     * @throws IllegalArgumentException si un paramètre est absent ou hors bornes (HTTP 400)
     */
    public static RechargeCredit creer(UUID organisateurId, long montantFcfa, NumeroTelephone telephone,
                                       long montantMinFcfa, long montantMaxFcfa, Instant maintenant) {
        if (organisateurId == null) {
            throw new IllegalArgumentException("L'organisateur est obligatoire");
        }
        if (telephone == null) {
            throw new IllegalArgumentException("Le numéro de téléphone est obligatoire");
        }
        if (maintenant == null) {
            throw new IllegalArgumentException("La date de création est obligatoire");
        }
        if (montantFcfa < montantMinFcfa || montantFcfa > montantMaxFcfa) {
            throw new IllegalArgumentException(
                "Le montant d'une recharge doit être compris entre %d et %d FCFA".formatted(montantMinFcfa, montantMaxFcfa));
        }
        return new RechargeCredit(UUID.randomUUID(), organisateurId, Money.ofFcfa(montantFcfa), telephone,
            maintenant.truncatedTo(ChronoUnit.MICROS), StatutRecharge.EN_ATTENTE, null, null, null);
    }

    /** Reconstruction depuis la persistance : l'état est supposé déjà valide en base. */
    public static RechargeCredit reconstituer(UUID id, UUID organisateurId, Money montant, NumeroTelephone telephone,
                                              Instant creeLe, StatutRecharge status, Instant traiteeLe,
                                              Long montantRecuFcfa, Paiement paiement) {
        return new RechargeCredit(id, organisateurId, montant, telephone, creeLe, status, traiteeLe, montantRecuFcfa,
            paiement);
    }

    private RechargeCredit(UUID id, UUID organisateurId, Money montant, NumeroTelephone telephone, Instant creeLe,
                           StatutRecharge status, Instant traiteeLe, Long montantRecuFcfa, Paiement paiement) {
        this.id = id;
        this.organisateurId = organisateurId;
        this.montant = montant;
        this.telephone = telephone;
        this.creeLe = creeLe;
        this.status = status;
        this.traiteeLe = traiteeLe;
        this.montantRecuFcfa = montantRecuFcfa;
        this.paiement = paiement;
    }

    /**
     * Enregistre le paiement ouvert chez la passerelle, une seule fois.
     *
     * @throws TransitionCommandeInterditeException si la recharge n'attend plus de paiement
     *                                              ou si un paiement est déjà ouvert (409)
     */
    public void ouvrirPaiement(Paiement nouveau) {
        if (nouveau == null) {
            throw new IllegalArgumentException("Le paiement est obligatoire");
        }
        if (status != StatutRecharge.EN_ATTENTE) {
            throw new TransitionCommandeInterditeException("Cette recharge n'attend plus de paiement");
        }
        if (paiement != null) {
            throw new TransitionCommandeInterditeException("Un paiement est déjà ouvert pour cette recharge");
        }
        paiement = nouveau;
    }

    /**
     * Paiement reçu (montant signé par la passerelle). Montant égal : PAYEE, le solde
     * doit être crédité. Montant différent : ANOMALIE, rien n'est crédité.
     *
     * @return vrai si le solde doit être crédité de {@link #getMontant()}
     * @throws TransitionCommandeInterditeException si la recharge n'est plus en attente
     */
    public boolean confirmer(long montantRecuFcfa, Instant maintenant) {
        exigerEnAttente();
        this.montantRecuFcfa = montantRecuFcfa;
        this.traiteeLe = maintenant.truncatedTo(ChronoUnit.MICROS);
        if (montantRecuFcfa != montant.valeur().longValueExact()) {
            status = StatutRecharge.ANOMALIE;
            return false;
        }
        status = StatutRecharge.PAYEE;
        return true;
    }

    /**
     * Paiement échoué ou expiré.
     *
     * @throws TransitionCommandeInterditeException si la recharge n'est plus en attente
     */
    public void echouer(Instant maintenant) {
        exigerEnAttente();
        status = StatutRecharge.ECHOUEE;
        traiteeLe = maintenant.truncatedTo(ChronoUnit.MICROS);
    }

    private void exigerEnAttente() {
        if (status != StatutRecharge.EN_ATTENTE) {
            throw new TransitionCommandeInterditeException("Cette recharge n'attend plus de paiement");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganisateurId() {
        return organisateurId;
    }

    public Money getMontant() {
        return montant;
    }

    public NumeroTelephone getTelephone() {
        return telephone;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public StatutRecharge getStatus() {
        return status;
    }

    public Instant getTraiteeLe() {
        return traiteeLe;
    }

    /** Montant signé reçu de la passerelle, ou null tant que la recharge n'est pas traitée. */
    public Long getMontantRecuFcfa() {
        return montantRecuFcfa;
    }

    /** Le paiement ouvert chez la passerelle, ou null s'il n'y en a pas encore. */
    public Paiement getPaiement() {
        return paiement;
    }
}
