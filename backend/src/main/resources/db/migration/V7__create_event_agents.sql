-- Module events (niveau 2, voir ADR 0002) : affectation d'un agent au
-- contrôle d'entrée d'un événement. La future route de scan n'accepte qu'un
-- agent affecté à l'événement contrôlé (contrat de ValidateTicketScanUseCase).

CREATE TABLE event_agents (
    event_id      UUID NOT NULL REFERENCES events(id),  -- même module : FK autorisée
    agent_id      UUID NOT NULL,  -- référence users.id (module auth) par UUID uniquement, pas de FK cross-module
    affecte_le    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, agent_id)
);

-- Événements auxquels un agent est affecté (son écran de contrôle).
CREATE INDEX idx_event_agents_agent_id ON event_agents (agent_id);
