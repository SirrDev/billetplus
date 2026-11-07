package com.billetplus.auth.domain;

/**
 * Statut de vérification d'un compte organisateur (ADR 0012).
 *
 * <pre>
 * A_VERIFIER ──pièces déposées et soumises──► EN_REVUE ──administrateur──► VERIFIE
 *     ▲                                          │                         │
 *     └──────────── nouveau dépôt ◄── REFUSE ◄───┘    changement ──────────┘ (retour EN_REVUE)
 * (tout statut) ──administrateur──► SUSPENDU (définitif dans l'application)
 * </pre>
 *
 * Seul VERIFIE permet de publier et de recevoir un reversement.
 */
public enum StatutVerification {
    A_VERIFIER,
    EN_REVUE,
    VERIFIE,
    REFUSE,
    SUSPENDU
}
