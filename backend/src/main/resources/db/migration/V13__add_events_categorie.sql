-- Categorie d'un evenement, pour filtrer le catalogue public (musique, sport...).
-- Les evenements existants passent en AUTRE ; la liste suit l'enum CategorieEvenement.
ALTER TABLE events ADD COLUMN categorie VARCHAR(20) NOT NULL DEFAULT 'AUTRE'
    CHECK (categorie IN ('MUSIQUE', 'SPORT', 'CONFERENCE', 'SPECTACLE', 'FESTIVAL', 'AUTRE'));

-- Filtre du catalogue public : evenements publies d'une categorie, par date.
CREATE INDEX idx_events_categorie_debute_le ON events (categorie, debute_le) WHERE status = 'PUBLIE';
