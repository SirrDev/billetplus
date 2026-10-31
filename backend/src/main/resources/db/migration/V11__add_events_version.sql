-- Verrouillage optimiste des evenements (@Version sur Event) : publier, annuler et
-- modifier lisent puis reecrivent la ligne entiere. Sans version, une modification
-- concurrente d'une annulation pouvait remettre l'ancien statut alors que les billets
-- etaient deja annules. Les lignes existantes partent de 0.
ALTER TABLE events ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
