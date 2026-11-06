package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.application.port.RechargeRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Paiement;
import com.billetplus.orders.domain.RechargeCredit;
import com.billetplus.shared.Money;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur du port RechargeRepository (ADR 0010). Même stratégie de sauvegarde que
 * {@link JpaOrderRepositoryAdapter} : l'entité gérée est rechargée et l'état du domaine y
 * est recopié, pour que la colonne version protège les transitions.
 *
 * {@code @Repository} convient : aucune BusinessConflictException n'est levée ici.
 */
@Repository
class JpaRechargeRepositoryAdapter implements RechargeRepository {

    private final SpringDataRechargeRepository springRepo;

    JpaRechargeRepositoryAdapter(SpringDataRechargeRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<RechargeCredit> findById(UUID id) {
        return springRepo.findById(id).map(JpaRechargeRepositoryAdapter::versDomaine);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RechargeCredit save(RechargeCredit recharge) {
        RechargeCreditJpaEntity entity = springRepo.findById(recharge.getId())
            .orElseGet(() -> new RechargeCreditJpaEntity(recharge.getId(), recharge.getOrganisateurId(),
                recharge.getMontant().valeur().longValueExact(), recharge.getTelephone().valeur(),
                recharge.getCreeLe()));
        entity.copierEtat(recharge.getStatus(), recharge.getTraiteeLe(), recharge.getMontantRecuFcfa());
        Paiement p = recharge.getPaiement();
        if (p == null) {
            entity.copierPaiement(null, null, null, null, null);
        } else {
            entity.copierPaiement(p.moyen(), p.reference(), p.transactionId(), p.url(), p.ouvertLe());
        }
        // Écrite tout de suite : le contrôle de version a lieu ici, AVANT le crédit du solde
        // (SQL direct, qui ne déclenche aucun flush). Un webhook concurrent échoue donc sur la
        // version (503, à relancer), pas sur l'index unique du journal.
        return versDomaine(springRepo.saveAndFlush(entity));
    }

    @Override
    public Optional<RechargeCredit> findByReferencePaiement(String reference) {
        return springRepo.findByPaiementReference(reference).map(JpaRechargeRepositoryAdapter::versDomaine);
    }

    @Override
    public List<RechargeCredit> dernieres(UUID organisateurId, int limite) {
        return springRepo.findByOrganisateurIdOrderByCreeLeDesc(organisateurId, PageRequest.of(0, limite)).stream()
            .map(JpaRechargeRepositoryAdapter::versDomaine)
            .toList();
    }

    private static RechargeCredit versDomaine(RechargeCreditJpaEntity e) {
        Paiement paiement = e.getPaiementReference() == null ? null : new Paiement(e.getPaiementMoyen(),
            e.getPaiementReference(), e.getPaiementTransactionId(), e.getPaiementUrl(), e.getPaiementOuvertLe());
        // Valeur déjà normalisée (CHECK en base) : depuis() la relit à l'identique.
        return RechargeCredit.reconstituer(e.getId(), e.getOrganisateurId(), Money.ofFcfa(e.getMontantFcfa()),
            NumeroTelephone.depuis(e.getTelephone()), e.getCreeLe(), e.getStatus(), e.getTraiteeLe(),
            e.getMontantRecuFcfa(), paiement);
    }
}
