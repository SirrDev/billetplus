package com.billetplus.shared.limitation;

import java.time.Duration;

/**
 * Compteur de requêtes par fenêtre fixe. Interface séparée de son
 * implémentation Redis pour tester l'intercepteur sans Redis.
 */
interface CompteurRequetes {

    /**
     * Ajoute une requête au compteur {@code cle} et renvoie le total de la fenêtre,
     * celle-ci comprise. Le compteur disparaît de lui-même après {@code fenetre}.
     */
    long incrementer(String cle, Duration fenetre);
}
