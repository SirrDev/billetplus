package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.ReservationTemporaire;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — stockage des réservations temporaires de
 * places pendant le tunnel de paiement (Redis, ADR 0003). L'implémentation
 * vit dans infrastructure/redis/.
 *
 * Les réservations ne modifient jamais ticket_categories : PostgreSQL reste la
 * source de vérité de la vente, et le dernier rempart contre la survente.
 */
public interface SeatReservationStore {

    /**
     * Enregistre la réservation si, une fois retirées les réservations expirées à
     * {@code maintenant}, la somme des places réservées par les AUTRES commandes
     * de la catégorie plus {@code reservation.getQuantite()} ne dépasse pas
     * {@code limite}. Vérification et écriture sont atomiques.
     *
     * Une réservation encore valide de la même commande est remplacée (nouvelle
     * quantité) mais garde son expiration d'origine : réserver de nouveau ne
     * prolonge jamais le blocage des places.
     *
     * @param limite places encore vendables, calculées par le domaine
     *               (TicketCategory.quotaRestant())
     * @return l'expiration effectivement enregistrée (celle de {@code reservation},
     *         ou l'expiration d'origine en cas de remplacement), ou vide si la
     *         réservation dépasserait la limite (rien n'est alors modifié)
     */
    Optional<Instant> reserver(ReservationTemporaire reservation, int limite, Instant maintenant);

    /** Supprime la réservation de la commande pour la catégorie. Sans effet si elle n'existe pas. */
    void liberer(UUID categoryId, UUID orderId);

    /**
     * Places de la catégorie bloquées par des réservations encore valides à
     * {@code maintenant} (les réservations expirées non encore nettoyées sont exclues).
     * Lecture seule, pour le tableau de bord.
     */
    int placesReservees(UUID categoryId, Instant maintenant);
}
