package com.billetplus.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/**
 * Ligne de commande telle que stockée dans order_lines (V8). Valeur sans identité
 * propre : elle n'existe qu'à l'intérieur de sa commande, d'où @Embeddable dans
 * une @ElementCollection plutôt qu'une entité. Les lignes ne changent jamais
 * après la création de la commande.
 */
@Embeddable
class OrderLineEmbeddable {

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(nullable = false, updatable = false)
    private int quantite;

    @Column(name = "prix_unitaire_fcfa", nullable = false, updatable = false)
    private long prixUnitaireFcfa;

    protected OrderLineEmbeddable() {
        // requis par JPA
    }

    OrderLineEmbeddable(UUID categoryId, int quantite, long prixUnitaireFcfa) {
        this.categoryId = categoryId;
        this.quantite = quantite;
        this.prixUnitaireFcfa = prixUnitaireFcfa;
    }

    UUID getCategoryId() {
        return categoryId;
    }

    int getQuantite() {
        return quantite;
    }

    long getPrixUnitaireFcfa() {
        return prixUnitaireFcfa;
    }
}
