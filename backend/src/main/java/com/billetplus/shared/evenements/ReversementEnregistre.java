package com.billetplus.shared.evenements;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement applicatif : un reversement vient d'être enregistré pour un organisateur
 * (ADR 0012). Publié par le module orders, écouté par le module auth, qui garde la date
 * du dernier reversement (point de départ de la conservation des pièces, 5 ans).
 */
public record ReversementEnregistre(UUID organisateurId, Instant enregistreLe) {
}
