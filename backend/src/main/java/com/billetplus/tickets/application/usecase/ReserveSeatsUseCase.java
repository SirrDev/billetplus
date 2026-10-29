package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.tickets.application.port.SeatReservationStore;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.domain.QuantiteParCommande;
import com.billetplus.tickets.domain.ReservationTemporaire;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.PlacesIndisponiblesException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — bloque des places d'une catégorie
 * pour une commande pendant le tunnel de paiement (ADR 0003), afin que le
 * quota soit garanti AVANT que l'acheteur paie.
 *
 * La limite vient du domaine ({@link TicketCategory#quotaRestant()}, lu dans
 * PostgreSQL) ; le stockage vérifie de façon atomique que les réservations des
 * autres commandes plus celle-ci y tiennent. Rien n'est écrit dans
 * ticket_categories : seule une vente payée consomme le quota
 * (IssueTicketUseCase).
 *
 * Risque résiduel connu : une vente validée entre la lecture de la limite et la
 * réservation peut, sur une fenêtre très courte, laisser accepter une
 * réservation de trop. PostgreSQL la refusera à l'émission ; l'acheteur, déjà
 * débité, devra être remboursé (module orders). Voir docs/HANDOFF.md.
 *
 * Pas de @Transactional : la lecture de la catégorie n'est pas suivie d'une
 * écriture en base.
 */
@Service
public class ReserveSeatsUseCase {

    private final TicketCategoryRepository categoryRepository;
    private final SeatReservationStore reservationStore;
    private final StatutEvenements statutEvenements;
    private final Clock clock;
    private final Duration dureeReservation;

    public ReserveSeatsUseCase(TicketCategoryRepository categoryRepository,
                               SeatReservationStore reservationStore,
                               StatutEvenements statutEvenements,
                               Clock clock,
                               @Value("${billetplus.reservation.duree:5m}") Duration dureeReservation) {
        this.categoryRepository = categoryRepository;
        this.reservationStore = reservationStore;
        this.statutEvenements = statutEvenements;
        this.clock = clock;
        this.dureeReservation = dureeReservation;
    }

    /**
     * Réserver de nouveau pour la même commande remplace la quantité mais garde
     * l'expiration d'origine : une commande ne peut pas prolonger indéfiniment
     * le blocage de places.
     *
     * <b>Contrat de l'appelant</b> (futur module orders) :
     * <ul>
     *   <li>{@code orderId} est généré par le serveur, rattaché à l'acheteur du jeton
     *       et persisté avant la réservation ; jamais lu dans le corps ni dans l'URL.
     *       Plafond de commandes en attente par compte et par événement, et limitation
     *       de débit par compte et par IP : sans eux, des réservations non payées
     *       peuvent épuiser une catégorie ;</li>
     *   <li>l'événement de la catégorie est vérifié publié et en vente avant de
     *       réserver ; sinon, même 404 qu'un identifiant inconnu (un brouillon reste
     *       indiscernable) ;</li>
     *   <li>la session de paiement Wave expire AVANT la réservation (durée strictement
     *       inférieure, avec une marge pour la latence du webhook), et le paiement
     *       n'est pas ouvert si la réservation a déjà expiré. Sinon, un paiement lent
     *       laisse expirer la réservation, un autre acheteur prend les places, et le
     *       premier est débité sans billet (QuotaDepasseException à l'émission).</li>
     * </ul>
     *
     * @return la réservation enregistrée, avec son expiration effective
     * @throws IllegalArgumentException     si un paramètre est absent ou si la quantité n'est pas
     *                                      comprise entre 1 et {@link QuantiteParCommande#MAX}
     * @throws NoSuchElementException       si la catégorie n'existe pas, ou si son événement n'est pas
     *                                      ouvert à la vente (même réponse : un brouillon reste indiscernable)
     * @throws PlacesIndisponiblesException si les places disponibles ne suffisent pas
     */
    public ReservationTemporaire execute(UUID categoryId, UUID orderId, int quantite) {
        if (categoryId == null || orderId == null) {
            throw new IllegalArgumentException("categoryId et orderId sont obligatoires");
        }
        QuantiteParCommande.verifier(quantite);

        TicketCategory category = categoryRepository.findById(categoryId)
            .filter(c -> statutEvenements.estOuvertALaVente(c.getEventId()))
            // Même réponse qu'une catégorie inconnue quand l'événement est un brouillon,
            // annulé ou terminé : la règle de visibilité du module events (un brouillon
            // est indiscernable d'un identifiant inconnu) s'applique aussi ici (ADR 0005).
            .orElseThrow(() -> new NoSuchElementException("Catégorie de billet introuvable : " + categoryId));

        Instant maintenant = Instant.now(clock);
        ReservationTemporaire demandee =
            ReservationTemporaire.creer(categoryId, orderId, quantite, maintenant, dureeReservation);

        Instant expireLe = reservationStore.reserver(demandee, category.quotaRestant(), maintenant)
            .orElseThrow(PlacesIndisponiblesException::new);
        return ReservationTemporaire.reconstituer(categoryId, orderId, quantite, expireLe);
    }
}
