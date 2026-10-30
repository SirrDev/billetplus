package com.billetplus.orders.application.port;

import java.time.Duration;

/**
 * Port (niveau 3, voir ADR 0002) — borne l'attente des verrous dans la transaction
 * courante (lock_timeout PostgreSQL). Sans borne, une émission concurrente sur la
 * même commande attendrait sans limite la fin de la première (contrat de orders).
 */
public interface DelaiAttenteVerrous {

    /** Exige une transaction ouverte : le délai ne vaut que pour elle. */
    void appliquer(Duration delai);
}
