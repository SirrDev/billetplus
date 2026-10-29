-- Module tickets (niveau 3, voir ADR 0002) : catégories de billets et billets.
-- Les identifiants sont générés en mémoire par le domaine (UUID.randomUUID()) ;
-- le DEFAULT ne sert qu'aux insertions manuelles, comme en V3.

CREATE TABLE ticket_categories (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID NOT NULL,  -- référence events.id par UUID uniquement, pas de FK cross-module
    nom           VARCHAR(255) NOT NULL,
    prix_fcfa     BIGINT NOT NULL CHECK (prix_fcfa >= 0),  -- Money : FCFA entiers, 0 = gratuit
    quota_total   INTEGER NOT NULL CHECK (quota_total > 0),
    quota_vendu   INTEGER NOT NULL DEFAULT 0,
    -- Verrouillage optimiste (@Version côté entité JPA, absent du domaine) :
    -- deux réservations concurrentes sur la même catégorie ne peuvent pas
    -- toutes deux être validées. Voir TicketCategoryRepository.
    version       BIGINT NOT NULL DEFAULT 0,
    -- Dernier rempart contre la survente, indépendant du code applicatif :
    -- même un UPDATE fautif ne peut pas dépasser le quota.
    CONSTRAINT chk_ticket_categories_quota_vendu
        CHECK (quota_vendu >= 0 AND quota_vendu <= quota_total)
);

CREATE INDEX idx_ticket_categories_event_id ON ticket_categories (event_id);

CREATE TABLE tickets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID NOT NULL,  -- référence events.id par UUID uniquement, pas de FK cross-module
    category_id   UUID NOT NULL REFERENCES ticket_categories(id),  -- même module : FK autorisée
    order_id      UUID NOT NULL,  -- référence orders.id par UUID uniquement, pas de FK cross-module
    status        VARCHAR(20) NOT NULL CHECK (status IN ('EMIS', 'SCANNE', 'ANNULE', 'REMBOURSE')),
    emis_le       TIMESTAMPTZ NOT NULL,
    scanne_le     TIMESTAMPTZ,
    -- Verrouillage optimiste : deux scans simultanés du même billet ne peuvent
    -- pas tous deux le faire passer à SCANNE (voir tickets-validate-scan-usecase).
    version       BIGINT NOT NULL DEFAULT 0,
    -- Un billet scanné porte toujours sa date de scan. L'inverse n'est pas
    -- exigé : un billet scanné puis remboursé (REMBOURSE) la conserve.
    CONSTRAINT chk_tickets_scanne_le
        CHECK (status <> 'SCANNE' OR scanne_le IS NOT NULL)
);

-- (order_id, category_id) : garde anti double émission d'IssueTicketUseCase,
-- et recherche des billets d'une commande (préfixe order_id).
CREATE INDEX idx_tickets_order_id_category_id ON tickets (order_id, category_id);
-- category_id : la FK n'est pas indexée automatiquement par PostgreSQL.
CREATE INDEX idx_tickets_category_id ON tickets (category_id);
CREATE INDEX idx_tickets_event_id ON tickets (event_id);
