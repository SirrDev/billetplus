CREATE TABLE venues (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom           VARCHAR(255) NOT NULL,
    adresse       TEXT NOT NULL,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    seating_plan  JSONB
);

-- Recherche géolocalisée différée (PostGIS déjà activé en V1, colonne
-- geography à ajouter dans une migration ultérieure dédiée — voir
-- Venue.java pour la justification de ce choix).
CREATE INDEX idx_venues_lat_lng ON venues (latitude, longitude);

CREATE TABLE events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titre         VARCHAR(255) NOT NULL,
    description   TEXT,
    debute_le     TIMESTAMPTZ NOT NULL,
    termine_le    TIMESTAMPTZ,
    venue_id      UUID NOT NULL REFERENCES venues(id),
    organizer_id  UUID NOT NULL,  -- référence auth.users.id par UUID uniquement, pas de FK cross-module
    status        VARCHAR(20) NOT NULL CHECK (status IN ('BROUILLON', 'PUBLIE', 'ANNULE', 'TERMINE')),
    cree_le       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_organizer_id ON events (organizer_id);
CREATE INDEX idx_events_status ON events (status);
CREATE INDEX idx_events_titre_trgm ON events USING GIN (titre gin_trgm_ops);
