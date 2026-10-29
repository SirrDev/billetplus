package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;

import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — registre d'idempotence des émissions de
 * billets (table ticket_issuances, migration V5).
 *
 * Une émission (commande, catégorie) ne peut être enregistrée qu'une fois.
 * L'enregistrement doit être fait AVANT toute réservation de quota, dans la
 * transaction de l'appelant : un second appel concurrent pour le même couple
 * attend alors la fin du premier, puis échoue si le premier a été validé.
 */
public interface TicketIssuanceRepository {

    /**
     * Enregistre l'émission immédiatement (pas à la fin de la transaction).
     *
     * @throws BilletsDejaEmisException si une émission existe déjà pour ce couple (commande, catégorie)
     */
    void enregistrer(UUID orderId, UUID categoryId, int quantite);
}
