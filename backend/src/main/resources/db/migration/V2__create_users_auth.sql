CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL,
    mot_de_passe_hash TEXT NOT NULL,
    nom_complet      VARCHAR(255) NOT NULL,
    role             VARCHAR(20) NOT NULL CHECK (role IN ('ORGANISATEUR', 'AGENT', 'ADMIN')),
    cree_le          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_role ON users (role);
