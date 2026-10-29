-- Module auth (niveau 2, voir ADR 0002) : un compte AGENT est créé par un
-- organisateur, et lui appartient. Un organisateur ne peut affecter à ses
-- événements que ses propres agents.
--
-- FK vers users : même table, même module, donc autorisée. Nulle pour les
-- comptes créés par inscription (ORGANISATEUR) ou hors API (ADMIN).

ALTER TABLE users ADD COLUMN cree_par UUID REFERENCES users(id);

-- Un agent a toujours un créateur. Aucun AGENT n'existait avant V6 (aucune
-- route ne permettait d'en créer), la contrainte s'applique donc sans reprise.
ALTER TABLE users ADD CONSTRAINT chk_users_agent_a_un_createur
    CHECK (role <> 'AGENT' OR cree_par IS NOT NULL);

-- Liste des agents d'un organisateur (GET /api/agents).
CREATE INDEX idx_users_cree_par ON users (cree_par);
