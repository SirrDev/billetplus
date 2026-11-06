-- Villes et pays des lieux, recherche par ville (ADR 0011). Liste fermee, tenue par
-- Billet+ : ajouter une ville = une nouvelle migration. Pays de l'UEMOA seulement (FCFA).
CREATE TABLE villes (
    code   VARCHAR(40) PRIMARY KEY CHECK (code ~ '^[A-Z]{2}-[A-Z-]+$'),
    nom    VARCHAR(100) NOT NULL,
    pays   VARCHAR(2) NOT NULL CHECK (pays IN ('SN', 'CI', 'ML', 'BF', 'NE', 'BJ', 'TG', 'GW')),
    ordre  INTEGER NOT NULL,   -- ordre d'affichage dans le pays (capitale d'abord)
    CONSTRAINT chk_villes_code_du_pays CHECK (left(code, 2) = pays),
    CONSTRAINT uq_villes_pays_nom UNIQUE (pays, nom)
);

INSERT INTO villes (code, nom, pays, ordre) VALUES
    ('SN-DAKAR', 'Dakar', 'SN', 1),
    ('SN-THIES', 'Thiès', 'SN', 2),
    ('SN-SAINT-LOUIS', 'Saint-Louis', 'SN', 3),
    ('SN-TOUBA', 'Touba', 'SN', 4),
    ('SN-MBOUR', 'Mbour', 'SN', 5),
    ('SN-KAOLACK', 'Kaolack', 'SN', 6),
    ('SN-ZIGUINCHOR', 'Ziguinchor', 'SN', 7),
    ('CI-ABIDJAN', 'Abidjan', 'CI', 1),
    ('CI-BOUAKE', 'Bouaké', 'CI', 2),
    ('CI-YAMOUSSOUKRO', 'Yamoussoukro', 'CI', 3),
    ('CI-SAN-PEDRO', 'San-Pédro', 'CI', 4),
    ('CI-KORHOGO', 'Korhogo', 'CI', 5),
    ('CI-DALOA', 'Daloa', 'CI', 6),
    ('ML-BAMAKO', 'Bamako', 'ML', 1),
    ('ML-SIKASSO', 'Sikasso', 'ML', 2),
    ('ML-SEGOU', 'Ségou', 'ML', 3),
    ('ML-MOPTI', 'Mopti', 'ML', 4),
    ('ML-KAYES', 'Kayes', 'ML', 5),
    ('BF-OUAGADOUGOU', 'Ouagadougou', 'BF', 1),
    ('BF-BOBO-DIOULASSO', 'Bobo-Dioulasso', 'BF', 2),
    ('BF-KOUDOUGOU', 'Koudougou', 'BF', 3),
    ('NE-NIAMEY', 'Niamey', 'NE', 1),
    ('NE-ZINDER', 'Zinder', 'NE', 2),
    ('NE-MARADI', 'Maradi', 'NE', 3),
    ('BJ-COTONOU', 'Cotonou', 'BJ', 1),
    ('BJ-PORTO-NOVO', 'Porto-Novo', 'BJ', 2),
    ('BJ-ABOMEY-CALAVI', 'Abomey-Calavi', 'BJ', 3),
    ('BJ-PARAKOU', 'Parakou', 'BJ', 4),
    ('TG-LOME', 'Lomé', 'TG', 1),
    ('TG-KARA', 'Kara', 'TG', 2),
    ('TG-SOKODE', 'Sokodé', 'TG', 3),
    ('GW-BISSAU', 'Bissau', 'GW', 1);

-- Chaque lieu a une ville (meme module events : cle etrangere permise). Les lieux
-- existants (donnees de developpement) recoivent Dakar, puis la valeur par defaut est
-- retiree : tout nouveau lieu doit porter sa ville explicitement.
ALTER TABLE venues ADD COLUMN ville_code VARCHAR(40) NOT NULL DEFAULT 'SN-DAKAR'
    CONSTRAINT fk_venues_ville REFERENCES villes (code);
ALTER TABLE venues ALTER COLUMN ville_code DROP DEFAULT;
CREATE INDEX idx_venues_ville_code ON venues (ville_code);
-- La recherche joint events -> venues -> villes : index de la cle etrangere events.venue_id.
CREATE INDEX idx_events_venue_id ON events (venue_id);
