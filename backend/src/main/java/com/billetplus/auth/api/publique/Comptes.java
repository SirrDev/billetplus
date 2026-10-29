package com.billetplus.auth.api.publique;

import java.util.UUID;

/**
 * Port public du module auth (ADR 0005) : le seul point d'entrée par lequel un
 * autre module peut lire une information sur un compte. Implémenté à
 * l'intérieur du module auth.
 */
public interface Comptes {

    /**
     * Vrai si {@code agentId} est un compte AGENT créé par {@code organisateurId}.
     * Faux pour tout autre cas (identifiant absent, inconnu, autre rôle, autre créateur).
     */
    boolean estAgentCreePar(UUID agentId, UUID organisateurId);
}
