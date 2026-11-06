-- Commission de Billet+ et reversements aux organisateurs (ADR 0009).

-- Taux de commission fige dans chaque commande a sa creation, en points de base
-- (500 = 5 %) : changer le taux plus tard ne modifie aucune vente passee. Les
-- commandes existantes recoivent 5 %, puis la valeur par defaut est retiree :
-- toute nouvelle commande doit porter son taux explicitement.
ALTER TABLE orders ADD COLUMN taux_commission_points_base INTEGER NOT NULL DEFAULT 500
    CONSTRAINT chk_orders_taux_commission CHECK (taux_commission_points_base BETWEEN 0 AND 10000);
ALTER TABLE orders ALTER COLUMN taux_commission_points_base DROP DEFAULT;

-- Virements faits a la main par l'administrateur vers l'organisateur d'un evenement.
-- Une ligne par virement, jamais modifiee ni supprimee : une erreur se corrige par
-- un reversement negatif, toujours motive.
CREATE TABLE reversements (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL,          -- reference events.id par UUID uniquement, pas de FK cross-module
    montant_fcfa    BIGINT NOT NULL CONSTRAINT chk_reversements_montant_non_nul CHECK (montant_fcfa <> 0),
    reference       VARCHAR(100) NOT NULL,  -- reference du virement (Wave, Orange Money)
    motif           VARCHAR(255),
    reverse_le      DATE NOT NULL,          -- date du virement, saisie par l'administrateur
    enregistre_par  UUID NOT NULL,          -- reference users.id (module auth) par UUID uniquement
    enregistre_le   TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_reversements_correction_motivee CHECK (montant_fcfa > 0 OR motif IS NOT NULL),
    -- Un virement ne s'enregistre qu'une fois (double clic, rejeu) : meme evenement et meme
    -- reference refuses (409). L'index unique sert aussi le total reverse par evenement.
    CONSTRAINT uq_reversements_event_reference UNIQUE (event_id, reference)
);
