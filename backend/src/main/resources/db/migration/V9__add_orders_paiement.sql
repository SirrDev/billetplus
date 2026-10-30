-- Paiement ouvert chez la passerelle UnitechPay (ADR 0007) : au plus un par commande.

ALTER TABLE orders
    ADD COLUMN paiement_moyen           VARCHAR(20) CHECK (paiement_moyen IN ('WAVE', 'ORANGE_MONEY')),
    -- Référence générée par la passerelle : clé de rapprochement du webhook (champ signé).
    ADD COLUMN paiement_reference       VARCHAR(100),
    -- Identifiant de transaction de la passerelle : informatif, jamais utilisé pour décider (non signé).
    ADD COLUMN paiement_transaction_id  BIGINT,
    ADD COLUMN paiement_url             TEXT,
    ADD COLUMN paiement_ouvert_le       TIMESTAMPTZ,
    -- Un paiement est complet ou absent : jamais à moitié enregistré.
    ADD CONSTRAINT chk_orders_paiement_complet
        CHECK (num_nulls(paiement_moyen, paiement_reference, paiement_transaction_id,
                         paiement_url, paiement_ouvert_le) IN (0, 5));

-- Une référence de la passerelle ne désigne qu'une commande (les NULL ne se gênent pas).
-- L'index sert aussi la recherche du webhook par référence.
CREATE UNIQUE INDEX uq_orders_paiement_reference ON orders (paiement_reference);
