package com.billetplus.auth.infrastructure;

import com.billetplus.auth.application.VerificationRepository;
import com.billetplus.auth.domain.StatutVerification;
import com.billetplus.auth.domain.VerificationOrganisateur;
import com.billetplus.shared.exception.BusinessConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur du port VerificationRepository. {@code @Component} (pas {@code @Repository}) :
 * le domaine lève des BusinessConflictException que la traduction des beans {@code @Repository}
 * changerait en 500 (piège du CLAUDE.md), même si elles ne passent pas par cet adaptateur.
 */
@Component
class JpaVerificationRepositoryAdapter implements VerificationRepository {

    private final SpringDataVerificationRepository springRepo;

    JpaVerificationRepositoryAdapter(SpringDataVerificationRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<VerificationOrganisateur> findById(UUID organisateurId) {
        return springRepo.findById(organisateurId);
    }

    @Override
    public VerificationOrganisateur save(VerificationOrganisateur verification) {
        // Tout de suite : un conflit de version (deux décisions simultanées) apparaît ici (409).
        try {
            return springRepo.saveAndFlush(verification);
        } catch (DataIntegrityViolationException e) {
            // Deux premières écritures simultanées pour le même organisateur (clé primaire).
            if (Contraintes.violee(e, "verifications_organisateurs_pkey")) {
                throw new BusinessConflictException("Votre vérification vient d'être modifiée : rechargez la page");
            }
            throw e;
        }
    }

    @Override
    public List<VerificationOrganisateur> findByStatut(StatutVerification statut) {
        return springRepo.findByStatutOrderBySoumisLeAsc(statut);
    }
}
