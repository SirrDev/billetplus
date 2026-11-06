-- Verrouillage optimiste des comptes (meme principe que V11 pour events).
-- Sans lui, une ecriture concurrente (changement de mot de passe, modification de
-- l'agent) reecrivait toute la ligne et pouvait annuler un retrait : desactive_le
-- revenait a NULL sans erreur. Conflit -> 409.
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
