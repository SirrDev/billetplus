-- Entree libre payee par l'organisateur sur un credit prepaye (ADR 0010).

-- Tarif par billet gratuit (FCFA), fige dans la commande a sa confirmation. NULL pour
-- une commande payante. Une commande gratuite est confirmee tout de suite : PAYEE.
ALTER TABLE orders ADD COLUMN tarif_entree_libre_fcfa INTEGER
    CONSTRAINT chk_orders_tarif_entree_libre CHECK (tarif_entree_libre_fcfa > 0);
ALTER TABLE orders ADD CONSTRAINT chk_orders_entree_libre_payee
    CHECK (tarif_entree_libre_fcfa IS NULL OR status = 'PAYEE');
-- Plafond de billets gratuits par numero et par evenement.
CREATE INDEX idx_orders_entree_libre_telephone ON orders (telephone, event_id)
    WHERE tarif_entree_libre_fcfa IS NOT NULL;

-- Solde de chaque organisateur, jamais negatif. Debit par une seule requete
-- conditionnelle (solde_fcfa >= montant) : deux commandes simultanees ne peuvent pas
-- depenser le meme franc.
CREATE TABLE credits_organisateurs (
    organisateur_id  UUID PRIMARY KEY,      -- reference users.id (module auth) par UUID uniquement
    solde_fcfa       BIGINT NOT NULL CONSTRAINT chk_credits_solde_positif CHECK (solde_fcfa >= 0),
    mis_a_jour_le    TIMESTAMPTZ NOT NULL
);

-- Recharges payees par l'organisateur via UnitechPay (ADR 0007, 0010).
CREATE TABLE recharges_credit (
    id                       UUID PRIMARY KEY,
    organisateur_id          UUID NOT NULL,  -- reference users.id par UUID uniquement
    montant_fcfa             BIGINT NOT NULL CONSTRAINT chk_recharges_montant_positif CHECK (montant_fcfa > 0),
    telephone                VARCHAR(13) NOT NULL CHECK (telephone ~ '^\+2217[0-9]{8}$'),
    status                   VARCHAR(20) NOT NULL CHECK (status IN ('EN_ATTENTE', 'PAYEE', 'ECHOUEE', 'ANOMALIE')),
    cree_le                  TIMESTAMPTZ NOT NULL,
    traitee_le               TIMESTAMPTZ,
    montant_recu_fcfa        BIGINT,         -- montant signe recu, garde pour une anomalie
    paiement_moyen           VARCHAR(20) CHECK (paiement_moyen IN ('WAVE', 'ORANGE_MONEY')),
    paiement_reference       VARCHAR(100),
    paiement_transaction_id  BIGINT,
    paiement_url             TEXT,
    paiement_ouvert_le       TIMESTAMPTZ,
    version                  BIGINT NOT NULL,
    CONSTRAINT chk_recharges_paiement_complet
        CHECK (num_nulls(paiement_moyen, paiement_reference, paiement_transaction_id,
                         paiement_url, paiement_ouvert_le) IN (0, 5)),
    CONSTRAINT chk_recharges_traitee CHECK ((status = 'EN_ATTENTE') = (traitee_le IS NULL))
);
CREATE UNIQUE INDEX uq_recharges_paiement_reference ON recharges_credit (paiement_reference);
CREATE INDEX idx_recharges_organisateur_cree_le ON recharges_credit (organisateur_id, cree_le DESC);

-- Journal des mouvements, jamais modifie : le solde doit toujours egaler sa somme.
-- Une recharge ne credite qu'une fois, une commande ne debite qu'une fois (index uniques).
CREATE TABLE mouvements_credit (
    id               UUID PRIMARY KEY,
    organisateur_id  UUID NOT NULL,
    montant_fcfa     BIGINT NOT NULL CONSTRAINT chk_mouvements_montant_non_nul CHECK (montant_fcfa <> 0),
    type             VARCHAR(20) NOT NULL CHECK (type IN ('RECHARGE', 'ENTREE_LIBRE')),
    recharge_id      UUID,                   -- recharges_credit.id (meme module)
    order_id         UUID,                   -- orders.id (meme module)
    cree_le          TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_mouvements_recharge
        CHECK (type <> 'RECHARGE' OR (montant_fcfa > 0 AND recharge_id IS NOT NULL AND order_id IS NULL)),
    CONSTRAINT chk_mouvements_entree_libre
        CHECK (type <> 'ENTREE_LIBRE' OR (montant_fcfa < 0 AND order_id IS NOT NULL AND recharge_id IS NULL))
);
CREATE UNIQUE INDEX uq_mouvements_recharge ON mouvements_credit (recharge_id) WHERE recharge_id IS NOT NULL;
CREATE UNIQUE INDEX uq_mouvements_order ON mouvements_credit (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX idx_mouvements_organisateur_cree_le ON mouvements_credit (organisateur_id, cree_le DESC);
