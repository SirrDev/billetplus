package com.billetplus.events.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Module events — niveau 2 (JPA-as-domain), voir ADR 0002. Un agent est
 * affecté au contrôle d'entrée d'un événement (table event_agents, V7).
 *
 * agentId référence un User du module auth PAR IDENTIFIANT UNIQUEMENT, sans
 * relation JPA ni FK (ADR 0001). La relation à l'événement reste un simple
 * identifiant : l'affectation est lue et écrite sans charger l'événement.
 */
@Entity
@Table(name = "event_agents")
@IdClass(AffectationAgent.Cle.class)
public class AffectationAgent {

    @Id
    private UUID eventId;

    @Id
    private UUID agentId;

    @Column(nullable = false, updatable = false)
    private Instant affecteLe;

    protected AffectationAgent() {
        // requis par JPA
    }

    public AffectationAgent(UUID eventId, UUID agentId) {
        if (eventId == null || agentId == null) {
            throw new IllegalArgumentException("eventId et agentId sont obligatoires");
        }
        this.eventId = eventId;
        this.agentId = agentId;
        this.affecteLe = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public Instant getAffecteLe() {
        return affecteLe;
    }

    /** Clé composite (event_id, agent_id), exigée par @IdClass. */
    public static class Cle implements Serializable {

        private UUID eventId;
        private UUID agentId;

        protected Cle() {
            // requis par JPA
        }

        public Cle(UUID eventId, UUID agentId) {
            this.eventId = eventId;
            this.agentId = agentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Cle autre)) return false;
            return Objects.equals(eventId, autre.eventId) && Objects.equals(agentId, autre.agentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, agentId);
        }
    }
}
