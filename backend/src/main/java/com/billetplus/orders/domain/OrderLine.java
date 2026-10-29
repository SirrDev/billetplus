package com.billetplus.orders.domain;

import com.billetplus.shared.Money;

import java.util.UUID;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — places demandées dans une catégorie.
 *
 * Le prix unitaire est FIGÉ à la création de la commande, lu côté serveur dans le
 * module tickets (jamais dans la requête du client) : un changement de prix
 * ultérieur ne modifie pas le montant à payer.
 *
 * @param categoryId catégorie de billets (module tickets, UUID brut, sans clé étrangère)
 * @param quantite   de 1 à {@link Order#QUANTITE_MAX_PAR_CATEGORIE}
 */
public record OrderLine(UUID categoryId, int quantite, Money prixUnitaire) {

    public OrderLine {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId est obligatoire");
        }
        if (quantite <= 0 || quantite > Order.QUANTITE_MAX_PAR_CATEGORIE) {
            throw new IllegalArgumentException(
                "La quantité doit être comprise entre 1 et %d".formatted(Order.QUANTITE_MAX_PAR_CATEGORIE));
        }
        if (prixUnitaire == null) {
            throw new IllegalArgumentException("Le prix unitaire est obligatoire");
        }
    }

    public Money sousTotal() {
        return prixUnitaire.fois(quantite);
    }
}
