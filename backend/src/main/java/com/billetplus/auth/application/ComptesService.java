package com.billetplus.auth.application;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.auth.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Implémentation du port public {@link Comptes} (ADR 0005). */
@Service
class ComptesService implements Comptes {

    private final UserRepository userRepository;

    ComptesService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estAgentCreePar(UUID agentId, UUID organisateurId) {
        return agentId != null && organisateurId != null && userRepository.findById(agentId)
            .map(user -> user.getRole() == Role.AGENT && user.estActif() && organisateurId.equals(user.getCreePar()))
            .orElse(false);
    }
}
