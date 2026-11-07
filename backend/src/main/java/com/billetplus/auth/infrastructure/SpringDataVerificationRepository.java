package com.billetplus.auth.infrastructure;

import com.billetplus.auth.domain.StatutVerification;
import com.billetplus.auth.domain.VerificationOrganisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Interface Spring Data — usage interne à infrastructure/ uniquement. */
interface SpringDataVerificationRepository extends JpaRepository<VerificationOrganisateur, UUID> {

    /** Servie par idx_verifications_statut (V19). */
    List<VerificationOrganisateur> findByStatutOrderBySoumisLeAsc(StatutVerification statut);
}
