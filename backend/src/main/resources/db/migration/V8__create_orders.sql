-- Module orders (niveau 3, voir ADR 0002) : commandes passées sans compte (ADR 0006).
-- Identifiants générés en mémoire par le domaine (UUID.randomUUID()).

CREATE TABLE orders (
    id                 UUID PRIMARY KEY,
    event_id           UUID NOT NULL,  -- référence events.id par UUID uniquement, pas de FK cross-module
    -- Format international normalisé par NumeroTelephone : un même numéro n'a
    -- qu'une écriture, sinon les limites par téléphone seraient contournables.
    telephone          VARCHAR(13) NOT NULL CHECK (telephone ~ '^\+2217[0-9]{8}$'),
    -- Empreinte SHA-256 du jeton d'accès, jamais le jeton lui-même (ADR 0006).
    empreinte_jeton    VARCHAR(64) NOT NULL CHECK (empreinte_jeton ~ '^[0-9a-f]{64}$'),
    status             VARCHAR(20) NOT NULL CHECK (status IN
                           ('EN_ATTENTE_PAIEMENT', 'PAYEE', 'PAYEE_SANS_BILLET', 'EXPIREE', 'ANNULEE')),
    cree_le            TIMESTAMPTZ NOT NULL,
    expire_le          TIMESTAMPTZ NOT NULL,
    payee_le           TIMESTAMPTZ,
    motif_sans_billet  VARCHAR(30) CHECK (motif_sans_billet IN
                           ('RESERVATION_EXPIREE', 'VENTE_FERMEE', 'PLACES_INDISPONIBLES')),
    -- Verrouillage optimiste (@Version côté entité JPA, absent du domaine) :
    -- deux confirmations simultanées de la même commande ne peuvent pas toutes
    -- deux être validées. Voir OrderRepository.
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_orders_expire_apres_creation CHECK (expire_le > cree_le),
    -- Une commande payée (avec ou sans billet) porte sa date de paiement, et elle seule.
    CONSTRAINT chk_orders_payee_le
        CHECK ((status IN ('PAYEE', 'PAYEE_SANS_BILLET')) = (payee_le IS NOT NULL)),
    -- Une commande à rembourser porte toujours son motif (décision B, ADR 0006).
    CONSTRAINT chk_orders_motif_sans_billet
        CHECK ((status = 'PAYEE_SANS_BILLET') = (motif_sans_billet IS NOT NULL))
);

-- Commandes d'un événement par statut : remboursements d'un événement annulé,
-- tableau de bord de l'organisateur.
CREATE INDEX idx_orders_event_id_status ON orders (event_id, status);
-- Limites par téléphone (commandes récentes, commandes en attente).
CREATE INDEX idx_orders_telephone_cree_le ON orders (telephone, cree_le);
-- Expiration des commandes non payées : index partiel, limité aux commandes en attente.
CREATE INDEX idx_orders_en_attente_expire_le ON orders (expire_le) WHERE status = 'EN_ATTENTE_PAIEMENT';

CREATE TABLE order_lines (
    order_id             UUID NOT NULL REFERENCES orders(id),  -- même module : FK autorisée
    category_id          UUID NOT NULL,  -- référence ticket_categories.id par UUID uniquement, pas de FK cross-module
    quantite             INTEGER NOT NULL CHECK (quantite BETWEEN 1 AND 20),  -- Order.QUANTITE_MAX_PAR_CATEGORIE
    prix_unitaire_fcfa   BIGINT NOT NULL CHECK (prix_unitaire_fcfa >= 0),  -- figé à la commande
    -- Une catégorie au plus une fois par commande (invariant du domaine), comme
    -- la garde d'émission ticket_issuances (order_id, category_id) de V5.
    PRIMARY KEY (order_id, category_id)
);
