package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.SeatReservationStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — rend immédiatement les places d'une
 * réservation temporaire (paiement échoué, annulé ou abandonné), sans attendre
 * son expiration.
 *
 * Idempotent : libérer une réservation déjà libérée, expirée ou jamais créée
 * est sans effet. Aucune vérification d'existence de la catégorie, pour la même
 * raison.
 *
 * <b>Contrat de l'appelant</b> (futur module orders) : ce cas d'usage ne
 * vérifie AUCUNE propriété. Quiconque l'appelle avec un couple (catégorie,
 * commande) libère la réservation de cette commande, y compris en plein paiement.
 * <ul>
 *   <li>aucune route ne prend {@code orderId} ni {@code categoryId} du client pour
 *       libérer : les deux sont lus dans la commande en base ;</li>
 *   <li>la libération ne se déclenche que sur une transition d'état côté serveur :
 *       échec ou annulation DÉFINITIFS confirmés par Wave, ou annulation par
 *       l'acheteur dont l'identité (jeton) est celle de la commande ;</li>
 *   <li>jamais sur un statut Wave ambigu ou en attente : un webhook « payé » tardif
 *       arriverait sans réservation, et l'acheteur risquerait d'être débité sans
 *       billet.</li>
 * </ul>
 */
@Service
public class ReleaseSeatsUseCase {

    private final SeatReservationStore reservationStore;

    public ReleaseSeatsUseCase(SeatReservationStore reservationStore) {
        this.reservationStore = reservationStore;
    }

    /** @throws IllegalArgumentException si un paramètre est absent */
    public void execute(UUID categoryId, UUID orderId) {
        if (categoryId == null || orderId == null) {
            throw new IllegalArgumentException("categoryId et orderId sont obligatoires");
        }
        reservationStore.liberer(categoryId, orderId);
    }
}
