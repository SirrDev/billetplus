package com.billetplus.orders.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Interface Spring Data — usage interne à infrastructure/ uniquement. */
interface SpringDataRechargeRepository extends JpaRepository<RechargeCreditJpaEntity, UUID> {

    Optional<RechargeCreditJpaEntity> findByPaiementReference(String reference);

    /** Servie par idx_recharges_organisateur_cree_le (V17). */
    List<RechargeCreditJpaEntity> findByOrganisateurIdOrderByCreeLeDesc(UUID organisateurId, Pageable page);
}
