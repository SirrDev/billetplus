package com.billetplus.auth.api.publique;

import java.util.Optional;
import java.util.UUID;

/**
 * Port public du module auth (ADR 0005) : le seul point d'entrée par lequel un
 * autre module peut lire une information sur un compte. Implémenté à
 * l'intérieur du module auth.
 */
public interface Comptes {

    /**
     * Vrai si {@code agentId} est un compte AGENT actif créé par {@code organisateurId}.
     * Faux pour tout autre cas (identifiant absent, inconnu, autre rôle, autre créateur, agent retiré).
     */
    boolean estAgentCreePar(UUID agentId, UUID organisateurId);

    /**
     * Vrai si {@code agentId} est un compte AGENT actif (jamais retiré par son organisateur).
     * Faux pour tout autre cas (identifiant absent, inconnu, autre rôle, agent retiré).
     */
    boolean estAgentActif(UUID agentId);

    /**
     * Vrai si {@code organisateurId} est un compte ORGANISATEUR actif dont la vérification est
     * acceptée (ADR 0012) : condition pour publier un événement et recevoir un reversement.
     */
    boolean estOrganisateurVerifie(UUID organisateurId);

    /**
     * Numéro de reversement vérifié (+2217XXXXXXXX) de l'organisateur, seulement s'il est
     * {@link #estOrganisateurVerifie vérifié} : l'administrateur ne vire que vers lui.
     */
    Optional<String> numeroReversementVerifie(UUID organisateurId);

    /** Titulaire du numéro de reversement vérifié (nom affiché à l'administrateur). */
    Optional<String> titulaireReversementVerifie(UUID organisateurId);
}
