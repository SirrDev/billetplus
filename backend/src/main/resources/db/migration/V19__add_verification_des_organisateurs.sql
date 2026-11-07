-- Verification des organisateurs et reversement apres l'evenement (ADR 0012).

-- Statut de verification d'un compte organisateur (module auth). Absence de ligne : A_VERIFIER.
-- Cle etrangere vers users : meme module (auth), donc permise.
CREATE TABLE verifications_organisateurs (
    organisateur_id         UUID PRIMARY KEY REFERENCES users (id),
    statut                  VARCHAR(20) NOT NULL
        CHECK (statut IN ('A_VERIFIER', 'EN_REVUE', 'VERIFIE', 'REFUSE', 'SUSPENDU')),
    type_titulaire          VARCHAR(20) CHECK (type_titulaire IN ('PERSONNE', 'STRUCTURE')),
    nom_structure           VARCHAR(255),
    ninea                   VARCHAR(30),
    numero_reversement      VARCHAR(13) CHECK (numero_reversement ~ '^\+2217[0-9]{8}$'),
    titulaire_reversement   VARCHAR(255),
    soumis_le               TIMESTAMPTZ,
    decide_le               TIMESTAMPTZ,
    decide_par              UUID,
    motif                   VARCHAR(500),
    dernier_reversement_le  TIMESTAMPTZ,   -- point de depart de la conservation des pieces (5 ans)
    version                 BIGINT NOT NULL,
    CONSTRAINT chk_verifications_motif CHECK ((statut IN ('REFUSE', 'SUSPENDU')) = (motif IS NOT NULL)),
    CONSTRAINT chk_verifications_structure
        CHECK (type_titulaire IS DISTINCT FROM 'STRUCTURE' OR (nom_structure IS NOT NULL AND ninea IS NOT NULL)),
    CONSTRAINT chk_verifications_soumise
        CHECK (statut NOT IN ('EN_REVUE', 'VERIFIE')
               OR (type_titulaire IS NOT NULL AND numero_reversement IS NOT NULL AND titulaire_reversement IS NOT NULL))
);
CREATE INDEX idx_verifications_statut ON verifications_organisateurs (statut, soumis_le);

-- Pieces deposees, chiffrees par l'application (AES-256-GCM : nonce + texte chiffre).
-- Une piece par type et par organisateur : un nouveau depot remplace l'ancien.
CREATE TABLE pieces_organisateurs (
    id               UUID PRIMARY KEY,
    organisateur_id  UUID NOT NULL REFERENCES verifications_organisateurs (organisateur_id),
    type             VARCHAR(30) NOT NULL CHECK (type IN ('IDENTITE', 'JUSTIFICATIF_REVERSEMENT', 'NINEA_RCCM')),
    type_mime        VARCHAR(30) NOT NULL CHECK (type_mime IN ('application/pdf', 'image/jpeg', 'image/png')),
    taille           INTEGER NOT NULL CHECK (taille BETWEEN 1 AND 5242880),
    contenu          BYTEA NOT NULL,
    depose_le        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_pieces_organisateur_type UNIQUE (organisateur_id, type)
);

-- Un organisateur suspendu est desactive comme un agent retire : connexion, jetons et
-- /api/auth/me refuses. L'administrateur ne l'est jamais.
ALTER TABLE users DROP CONSTRAINT chk_users_seul_agent_desactive;
ALTER TABLE users ADD CONSTRAINT chk_users_desactivation
    CHECK (desactive_le IS NULL OR role IN ('AGENT', 'ORGANISATEUR'));

-- Suspension d'un evenement par l'administrateur : annulation, avec son motif.
ALTER TABLE events ADD COLUMN suspendu_motif VARCHAR(500);
ALTER TABLE events ADD CONSTRAINT chk_events_suspension
    CHECK (suspendu_motif IS NULL OR status = 'ANNULE');

-- Reversement sans billet scanne : derogation motivee de l'administrateur (entree sans controle).
ALTER TABLE reversements ADD COLUMN derogation_scans VARCHAR(255);
-- Copie du numero verifie vers lequel le virement a ete fait (preuve, meme si l'organisateur
-- change de numero ensuite).
ALTER TABLE reversements ADD COLUMN numero_reversement VARCHAR(13);
