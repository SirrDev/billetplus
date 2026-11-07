package com.billetplus.auth.application;

/**
 * Port (niveau 2) — chiffrement des pièces des organisateurs (ADR 0012). La clé n'est connue
 * que de l'implémentation (infrastructure) : jamais en base, jamais journalisée.
 */
public interface ChiffrementPieces {

    byte[] chiffrer(byte[] clair);

    /** @throws IllegalStateException si le contenu a été altéré ou chiffré avec une autre clé */
    byte[] dechiffrer(byte[] chiffre);
}
