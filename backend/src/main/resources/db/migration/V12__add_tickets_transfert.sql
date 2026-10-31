-- Transfert d'un billet a un autre mobile (ADR 0008).
--
-- qr_signe_le : date signee dans le QR en vigueur (champ iat). Egale a emis_le
-- jusqu'au transfert, qui la change : l'ancien QR est alors refuse au controle en
-- ligne. Remplie depuis emis_le pour les billets existants.
ALTER TABLE tickets ADD COLUMN qr_signe_le TIMESTAMPTZ;
UPDATE tickets SET qr_signe_le = emis_le;
ALTER TABLE tickets ALTER COLUMN qr_signe_le SET NOT NULL;

-- Destinataire (mobile au format international) et empreinte SHA-256 de son jeton :
-- le jeton lui-meme n'est jamais stocke.
ALTER TABLE tickets ADD COLUMN transfere_a VARCHAR(13);
-- VARCHAR(64) et format controle, comme orders.empreinte_jeton (V8).
ALTER TABLE tickets ADD COLUMN transfert_jeton_empreinte VARCHAR(64)
    CHECK (transfert_jeton_empreinte ~ '^[0-9a-f]{64}$');
ALTER TABLE tickets ADD COLUMN transfere_le TIMESTAMPTZ;

-- Un transfert est complet ou absent : jamais un numero sans jeton.
ALTER TABLE tickets ADD CONSTRAINT chk_tickets_transfert_complet CHECK (
    (transfere_a IS NULL AND transfert_jeton_empreinte IS NULL AND transfere_le IS NULL)
    OR (transfere_a IS NOT NULL AND transfert_jeton_empreinte IS NOT NULL AND transfere_le IS NOT NULL)
);
ALTER TABLE tickets ADD CONSTRAINT chk_tickets_transfere_a
    CHECK (transfere_a IS NULL OR transfere_a ~ '^\+2217[0-9]{8}$');
