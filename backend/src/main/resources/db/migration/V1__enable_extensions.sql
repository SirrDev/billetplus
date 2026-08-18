-- Extensions PostgreSQL requises par le projet — voir ADR 0003.
--
-- uuid-ossp    : génération d'UUID côté base (gen_random_uuid() natif depuis PG13+,
--                gardé ici pour compatibilité explicite)
-- pgcrypto     : gen_random_uuid() et fonctions cryptographiques utilitaires
-- pg_trgm      : recherche texte par trigrammes (artiste, titre d'événement, lieu)
-- postgis      : recherche géolocalisée ("événements à moins de 5 km")

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS postgis;
