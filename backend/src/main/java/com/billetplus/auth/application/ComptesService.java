package com.billetplus.auth.application;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.VerificationOrganisateur;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Implémentation du port public {@link Comptes} (ADR 0005). */
@Service
class ComptesService implements Comptes {

    private final UserRepository userRepository;
    private final VerificationRepository verificationRepository;

    ComptesService(UserRepository userRepository, VerificationRepository verificationRepository) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estOrganisateurVerifie(UUID organisateurId) {
        return verifiee(organisateurId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> numeroReversementVerifie(UUID organisateurId) {
        return verifiee(organisateurId).map(VerificationOrganisateur::getNumeroReversement);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> titulaireReversementVerifie(UUID organisateurId) {
        return verifiee(organisateurId).map(VerificationOrganisateur::getTitulaireReversement);
    }

    /** Compte organisateur actif ET vérification acceptée ; vide sinon. */
    private Optional<VerificationOrganisateur> verifiee(UUID organisateurId) {
        if (organisateurId == null) {
            return Optional.empty();
        }
        boolean actif = userRepository.findById(organisateurId)
            .map(user -> user.getRole() == Role.ORGANISATEUR && user.estActif())
            .orElse(false);
        return actif ? verificationRepository.findById(organisateurId).filter(VerificationOrganisateur::estVerifie)
            : Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estAgentCreePar(UUID agentId, UUID organisateurId) {
        return agentId != null && organisateurId != null && userRepository.findById(agentId)
            .map(user -> user.getRole() == Role.AGENT && user.estActif() && organisateurId.equals(user.getCreePar()))
            .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estAgentActif(UUID agentId) {
        return agentId != null && userRepository.findById(agentId)
            .map(user -> user.getRole() == Role.AGENT && user.estActif())
            .orElse(false);
    }
}
