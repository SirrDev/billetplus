package com.billetplus.events.domain;

/**
 * Villes de la liste V18 pour les tests (ADR 0011). Le code de production n'a aucune ville
 * par défaut : tout lieu porte la sienne explicitement.
 */
public final class VillesDeTest {

    public static final String DAKAR = "SN-DAKAR";

    private VillesDeTest() {
    }

    public static Ville dakar() {
        return new Ville(DAKAR, "Dakar", Pays.SN, 1);
    }
}
