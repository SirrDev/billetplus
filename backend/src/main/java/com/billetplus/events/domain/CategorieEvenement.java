package com.billetplus.events.domain;

import java.util.Locale;

/**
 * Catégorie d'un événement, pour filtrer le catalogue public. Liste fermée : un
 * filtre n'a de sens que sur des valeurs connues. AUTRE pour ce qui n'entre nulle
 * part, et pour les événements créés avant les catégories (V13).
 */
public enum CategorieEvenement {
    MUSIQUE,
    SPORT,
    CONFERENCE,
    SPECTACLE,
    FESTIVAL,
    AUTRE;

    /**
     * Lit une catégorie passée en paramètre de requête (casse ignorée).
     *
     * @throws IllegalArgumentException (400) si la valeur n'est pas une catégorie connue
     */
    public static CategorieEvenement lire(String valeur) {
        try {
            return valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Catégorie inconnue : " + valeur);
        }
    }
}
