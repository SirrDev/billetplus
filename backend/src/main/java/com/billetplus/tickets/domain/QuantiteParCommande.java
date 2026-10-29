package com.billetplus.tickets.domain;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — nombre de places qu'une commande
 * peut demander pour une catégorie, en réservation comme en émission.
 *
 * Le plafond borne la durée de la transaction d'émission (une insertion et une
 * signature par billet, catégorie verrouillée pendant ce temps) et le nombre
 * de places qu'une seule commande peut bloquer. Le module orders devra
 * appliquer la même limite à la création de la commande.
 */
public final class QuantiteParCommande {

    public static final int MAX = 20;

    private QuantiteParCommande() {
    }

    /** @throws IllegalArgumentException si la quantité n'est pas comprise entre 1 et {@link #MAX} */
    public static void verifier(int quantite) {
        if (quantite <= 0 || quantite > MAX) {
            throw new IllegalArgumentException("La quantité doit être comprise entre 1 et %d".formatted(MAX));
        }
    }
}
