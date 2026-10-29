package com.billetplus.events.api.publique;

import java.util.UUID;

/**
 * Port public du module events (ADR 0005) : le seul point d'entrée par lequel
 * un autre module peut lire l'état d'un événement. Implémenté à l'intérieur
 * du module events ; les autres modules ne connaissent que cette interface.
 */
public interface StatutEvenements {

    /**
     * Vrai si l'événement existe et est publié (ni brouillon, ni annulé, ni
     * terminé) : c'est la seule situation où l'on peut réserver des places ou
     * émettre des billets.
     */
    boolean estOuvertALaVente(UUID eventId);
}
