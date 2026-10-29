-- Module tickets (niveau 3, voir ADR 0002) : registre d'idempotence des émissions.
--
-- Une émission = un lot de billets d'une catégorie pour une commande. La clé
-- primaire (order_id, category_id) garantit qu'un même lot ne peut être émis
-- qu'une fois, même si deux confirmations de paiement arrivent en même temps
-- (webhook Wave rejoué). IssueTicketUseCase insère cette ligne AVANT de
-- réserver le quota : un second appel concurrent attend la fin du premier,
-- puis échoue sur la clé primaire (409) si le premier a été validé.
--
-- Remplace la vérification « lire puis écrire » existsByOrderIdAndCategoryId,
-- qui n'était pas atomique. V4 n'est pas modifiée (déjà appliquée).

CREATE TABLE ticket_issuances (
    order_id      UUID NOT NULL,  -- référence orders.id par UUID uniquement, pas de FK cross-module
    category_id   UUID NOT NULL REFERENCES ticket_categories(id),  -- même module : FK autorisée
    quantite      INTEGER NOT NULL CHECK (quantite > 0),
    emise_le      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (order_id, category_id)
);

-- category_id : la FK n'est pas indexée automatiquement par PostgreSQL, et la
-- clé primaire (préfixe order_id) ne sert pas une recherche par catégorie.
CREATE INDEX idx_ticket_issuances_category_id ON ticket_issuances (category_id);
