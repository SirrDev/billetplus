-- Deux motifs de commande à rembourser (branche n° 10) : un paiement reçu ne doit
-- jamais être perdu, même s'il ne correspond pas à la commande.
--   MONTANT_INCORRECT          : montant payé différent de celui de la commande ;
--   PAIEMENT_APRES_ANNULATION  : paiement reçu pour une commande déjà annulée.
-- La contrainte CHECK de V8 n'a pas de nom explicite : PostgreSQL l'a nommée
-- orders_motif_sans_billet_check.

ALTER TABLE orders DROP CONSTRAINT orders_motif_sans_billet_check;
ALTER TABLE orders ADD CONSTRAINT orders_motif_sans_billet_check
    CHECK (motif_sans_billet IN ('RESERVATION_EXPIREE', 'VENTE_FERMEE', 'PLACES_INDISPONIBLES',
                                 'MONTANT_INCORRECT', 'PAIEMENT_APRES_ANNULATION'));
