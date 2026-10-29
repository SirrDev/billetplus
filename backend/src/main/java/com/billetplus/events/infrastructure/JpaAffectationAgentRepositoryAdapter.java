package com.billetplus.events.infrastructure;

import com.billetplus.events.application.AffectationAgentRepository;
import com.billetplus.events.domain.AffectationAgent;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Adaptateur du port AffectationAgentRepository. L'appelant vérifie l'existence
 * avant d'enregistrer (affectation idempotente) : save() sur une clé déjà
 * présente ferait un merge, inoffensif ici puisque la ligne n'a pas d'autre
 * colonne modifiable (affecte_le est updatable = false).
 */
@Repository
class JpaAffectationAgentRepositoryAdapter implements AffectationAgentRepository {

    private final SpringDataAffectationAgentRepository springRepo;

    JpaAffectationAgentRepositoryAdapter(SpringDataAffectationAgentRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public boolean existe(UUID eventId, UUID agentId) {
        return springRepo.existsById(new AffectationAgent.Cle(eventId, agentId));
    }

    @Override
    public AffectationAgent save(AffectationAgent affectation) {
        return springRepo.save(affectation);
    }

    @Override
    public void supprimer(UUID eventId, UUID agentId) {
        springRepo.deleteById(new AffectationAgent.Cle(eventId, agentId));
    }
}
