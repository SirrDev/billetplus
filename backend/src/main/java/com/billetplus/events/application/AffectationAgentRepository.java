package com.billetplus.events.application;

import com.billetplus.events.domain.AffectationAgent;

import java.util.List;
import java.util.UUID;

/** Port (niveau 2) des affectations d'agents aux événements. */
public interface AffectationAgentRepository {

    boolean existe(UUID eventId, UUID agentId);

    AffectationAgent save(AffectationAgent affectation);

    /** Sans effet si l'affectation n'existe pas. */
    void supprimer(UUID eventId, UUID agentId);

    /** Toutes les affectations de cet agent, quel que soit l'événement. */
    void supprimerToutes(UUID agentId);

    /** Agents affectés à cet événement, du plus ancien au plus récent. */
    List<UUID> listerAgents(UUID eventId);
}
