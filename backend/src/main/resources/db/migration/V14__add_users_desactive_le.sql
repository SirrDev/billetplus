-- Retrait d'un agent par son organisateur : le compte est desactive, jamais supprime,
-- pour que l'historique des scans garde une reference valide. Null : compte actif.
ALTER TABLE users ADD COLUMN desactive_le TIMESTAMPTZ;

-- Seul un compte AGENT peut etre desactive (pas d'organisateur ni d'administrateur).
ALTER TABLE users ADD CONSTRAINT chk_users_seul_agent_desactive
    CHECK (desactive_le IS NULL OR role = 'AGENT');
