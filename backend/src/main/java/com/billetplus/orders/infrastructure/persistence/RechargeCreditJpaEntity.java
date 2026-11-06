package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.StatutRecharge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité JPA d'une recharge de crédit (V17, ADR 0010), distincte du domaine
 * {@link com.billetplus.orders.domain.RechargeCredit}. Package-private : seul
 * l'adaptateur de ce package la manipule.
 */
@Entity
@Table(name = "recharges_credit")
class RechargeCreditJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organisateurId;

    @Column(nullable = false, updatable = false)
    private long montantFcfa;

    @Column(nullable = false, updatable = false, length = 13)
    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutRecharge status;

    @Column(nullable = false, updatable = false)
    private Instant creeLe;

    private Instant traiteeLe;

    private Long montantRecuFcfa;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MoyenPaiement paiementMoyen;

    @Column(length = 100)
    private String paiementReference;

    private Long paiementTransactionId;

    @Column(columnDefinition = "TEXT")
    private String paiementUrl;

    private Instant paiementOuvertLe;

    /** Verrouillage optimiste, absent du domaine. Null à la création : persist au lieu de merge. */
    @Version
    @Column(nullable = false)
    private Long version;

    protected RechargeCreditJpaEntity() {
        // requis par JPA
    }

    RechargeCreditJpaEntity(UUID id, UUID organisateurId, long montantFcfa, String telephone, Instant creeLe) {
        this.id = id;
        this.organisateurId = organisateurId;
        this.montantFcfa = montantFcfa;
        this.telephone = telephone;
        this.creeLe = creeLe;
    }

    void copierEtat(StatutRecharge status, Instant traiteeLe, Long montantRecuFcfa) {
        this.status = status;
        this.traiteeLe = traiteeLe;
        this.montantRecuFcfa = montantRecuFcfa;
    }

    void copierPaiement(MoyenPaiement moyen, String reference, Long transactionId, String url, Instant ouvertLe) {
        this.paiementMoyen = moyen;
        this.paiementReference = reference;
        this.paiementTransactionId = transactionId;
        this.paiementUrl = url;
        this.paiementOuvertLe = ouvertLe;
    }

    UUID getId() {
        return id;
    }

    UUID getOrganisateurId() {
        return organisateurId;
    }

    long getMontantFcfa() {
        return montantFcfa;
    }

    String getTelephone() {
        return telephone;
    }

    StatutRecharge getStatus() {
        return status;
    }

    Instant getCreeLe() {
        return creeLe;
    }

    Instant getTraiteeLe() {
        return traiteeLe;
    }

    Long getMontantRecuFcfa() {
        return montantRecuFcfa;
    }

    MoyenPaiement getPaiementMoyen() {
        return paiementMoyen;
    }

    String getPaiementReference() {
        return paiementReference;
    }

    Long getPaiementTransactionId() {
        return paiementTransactionId;
    }

    String getPaiementUrl() {
        return paiementUrl;
    }

    Instant getPaiementOuvertLe() {
        return paiementOuvertLe;
    }
}
