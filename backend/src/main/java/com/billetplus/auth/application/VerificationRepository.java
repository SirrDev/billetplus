package com.billetplus.auth.application;

import com.billetplus.auth.domain.StatutVerification;
import com.billetplus.auth.domain.VerificationOrganisateur;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port (niveau 2) — vérifications des organisateurs (ADR 0012). */
public interface VerificationRepository {

    Optional<VerificationOrganisateur> findById(UUID organisateurId);

    /** Écrit tout de suite : le contrôle de version a lieu ici (deux décisions simultanées). */
    VerificationOrganisateur save(VerificationOrganisateur verification);

    /** Vérifications de ce statut, les plus anciennes demandes d'abord. */
    List<VerificationOrganisateur> findByStatut(StatutVerification statut);
}
