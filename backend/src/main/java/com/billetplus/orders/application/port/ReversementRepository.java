package com.billetplus.orders.application.port;

import com.billetplus.orders.domain.Reversement;

import java.util.Map;
import java.util.UUID;

/** Port (niveau 3, ADR 0002) — reversements aux organisateurs (ADR 0009). */
public interface ReversementRepository {

    /**
     * Exige une transaction ouverte par l'appelant.
     *
     * @throws com.billetplus.shared.exception.BusinessConflictException (409) si ce virement
     *         (même événement, même référence) est déjà enregistré
     */
    Reversement save(Reversement reversement);

    /** Vrai si l'événement a au moins un reversement (même si leur somme vaut 0). */
    boolean existePour(UUID eventId);

    /** Somme signée des reversements de cet événement, 0 s'il n'y en a aucun. */
    long totalReverse(UUID eventId);

    /** Somme signée des reversements, par événement (seuls les événements qui en ont). */
    Map<UUID, Long> totauxParEvenement();
}
