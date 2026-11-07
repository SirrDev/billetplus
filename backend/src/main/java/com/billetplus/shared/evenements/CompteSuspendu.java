package com.billetplus.shared.evenements;

import java.util.UUID;

/**
 * Événement applicatif : un compte organisateur vient d'être suspendu par l'administrateur
 * (ADR 0012). Publié par le module auth, écouté par le module events, qui annule tous les
 * événements à venir de l'organisateur. Écouteur synchrone : même transaction.
 */
public record CompteSuspendu(UUID organisateurId) {
}
