package com.billetplus.events.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Pays ouverts aux lieux (ADR 0011) : les 8 pays de l'UEMOA, tous en FCFA. Ordre
 * d'affichage. Les codes sont ceux de la contrainte CHECK de la table villes (V18).
 */
public enum Pays {
    SN("Sénégal"),
    CI("Côte d'Ivoire"),
    ML("Mali"),
    BF("Burkina Faso"),
    NE("Niger"),
    BJ("Bénin"),
    TG("Togo"),
    GW("Guinée-Bissau");

    private final String nom;

    Pays(String nom) {
        this.nom = nom;
    }

    public String getNom() {
        return nom;
    }

    /** Code ISO 3166-1 alpha-2 exact (majuscules) ; vide pour tout autre texte. */
    public static Optional<Pays> depuisCode(String code) {
        return Arrays.stream(values()).filter(p -> p.name().equals(code)).findFirst();
    }
}
