---
paths:
  - "backend/src/main/resources/db/migration/*.sql"
---

# Migrations Flyway

- **Ne jamais modifier une migration déjà appliquée** (V1, V2, V3...) : Flyway en contrôle la somme de contrôle et l'application refuserait de démarrer. Toute évolution = une nouvelle migration `V<n+1>__description.sql`.
- Hibernate est en `ddl-auto: validate` : le schéma est la source de vérité, les entités doivent s'y aligner (types, longueurs, `NOT NULL`).
- Chaque `VARCHAR(n)` impose un `@Size(max = n)` sur le DTO d'entrée correspondant (sinon 500 au lieu de 400).
- **Aucune `FOREIGN KEY` entre tables de modules différents** (ex. `events.organizer_id` vers `users.id` reste une simple colonne `UUID`). Les contraintes à l'intérieur d'un même module sont autorisées.
- Le plan de salle est en `JSONB`, la géolocalisation utilise PostGIS : les types doivent exister dans l'image `postgis/postgis:16-3.4`.
