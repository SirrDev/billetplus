package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.exception.TropDeCommandesEnAttenteException;
import com.billetplus.shared.Money;
import com.billetplus.tickets.api.publique.PlacesEnVente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — crée une commande sans compte
 * (ADR 0006) et bloque ses places AVANT tout paiement.
 *
 * Tout se fait dans UNE transaction : la commande est enregistrée, puis chaque
 * catégorie est réservée (Redis, ADR 0003). Si une réservation échoue, l'exception
 * annule la transaction : la commande n'existe pas en base, et les places déjà
 * réservées pour elle sont rendues après l'annulation (afterCompletion). Le même
 * mécanisme couvre un échec de la validation elle-même. Aucune commande ne reste
 * donc en attente de paiement sans ses places, et l'orderId est généré par le
 * serveur, jamais lu dans la requête (contrat de ReserveSeatsUseCase).
 *
 * Délai de paiement strictement plus court que la réservation (vérifié au
 * démarrage) : une commande expire toujours avant ses places, donc un paiement
 * dans le délai trouve ses places encore bloquées.
 */
@Service
public class CreateOrderUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateOrderUseCase.class);

    /** Une catégorie et un nombre de places, tels que demandés par l'acheteur (sans prix). */
    public record LigneDemandee(UUID categoryId, int quantite) {
    }

    private final OrderRepository orderRepository;
    private final PlacesEnVente placesEnVente;
    private final Clock clock;
    private final Duration delaiPaiement;
    private final int maxEnAttenteParTelephone;

    public CreateOrderUseCase(OrderRepository orderRepository, PlacesEnVente placesEnVente, Clock clock,
                              @Value("${billetplus.commande.delai-paiement:4m}") Duration delaiPaiement,
                              @Value("${billetplus.reservation.duree:5m}") Duration dureeReservation,
                              @Value("${billetplus.commande.max-en-attente-par-telephone:3}") int maxEnAttenteParTelephone) {
        if (delaiPaiement.isZero() || delaiPaiement.isNegative() || delaiPaiement.compareTo(dureeReservation) >= 0) {
            // Échec au démarrage plutôt qu'en production : sinon un paiement dans le
            // délai pourrait arriver après l'expiration des places (payé sans billet).
            throw new IllegalStateException("billetplus.commande.delai-paiement (%s) doit être positif et "
                .formatted(delaiPaiement) + "strictement inférieur à billetplus.reservation.duree (%s)"
                .formatted(dureeReservation));
        }
        if (maxEnAttenteParTelephone < 1) {
            throw new IllegalStateException("billetplus.commande.max-en-attente-par-telephone doit être au moins 1");
        }
        this.orderRepository = orderRepository;
        this.placesEnVente = placesEnVente;
        this.clock = clock;
        this.delaiPaiement = delaiPaiement;
        this.maxEnAttenteParTelephone = maxEnAttenteParTelephone;
    }

    /**
     * @return la commande enregistrée et son jeton d'accès en clair, à remettre une
     *         seule fois à l'acheteur (ADR 0006)
     * @throws IllegalArgumentException            (400) paramètre absent, téléphone invalide,
     *                                             commande vide, trop grande ou catégorie en double
     * @throws NoSuchElementException              (404) catégorie inconnue, d'un autre événement,
     *                                             ou événement fermé à la vente
     * @throws TropDeCommandesEnAttenteException   (409) limite par téléphone atteinte
     * @throws com.billetplus.shared.exception.BusinessConflictException (409) places insuffisantes
     */
    @Transactional
    public Order.CommandeCreee execute(UUID eventId, String telephoneSaisi, List<LigneDemandee> demandees) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId est obligatoire");
        }
        NumeroTelephone telephone = NumeroTelephone.depuis(telephoneSaisi);
        if (demandees == null || demandees.isEmpty()) {
            throw new IllegalArgumentException("Une commande contient au moins une catégorie");
        }
        if (demandees.size() > Order.LIGNES_MAX) {
            // Avant toute lecture : une requête de 10 000 lignes ne doit pas déclencher 10 000 lectures.
            throw new IllegalArgumentException("Une commande contient au plus %d catégories".formatted(Order.LIGNES_MAX));
        }

        List<OrderLine> lignes = new ArrayList<>();
        for (LigneDemandee demandee : demandees) {
            if (demandee == null || demandee.categoryId() == null) {
                throw new IllegalArgumentException("categoryId est obligatoire");
            }
            // Prix lu côté serveur, jamais dans la requête ; même 404 pour une catégorie
            // inconnue, d'un autre événement ou d'un événement fermé à la vente.
            Money prix = placesEnVente.prixSiEnVente(eventId, demandee.categoryId())
                .orElseThrow(() -> new NoSuchElementException("Catégorie de billet introuvable : " + demandee.categoryId()));
            lignes.add(new OrderLine(demandee.categoryId(), demandee.quantite(), prix));
        }

        Instant maintenant = Instant.now(clock);
        // Limite souple : deux créations simultanées pour le même numéro peuvent toutes
        // deux passer. Acceptable : le vrai rempart est la limitation par IP (rate-limiting)
        // et la courte durée des réservations.
        if (orderRepository.compterEnAttente(telephone, eventId, maintenant) >= maxEnAttenteParTelephone) {
            throw new TropDeCommandesEnAttenteException();
        }

        Order.CommandeCreee creee = Order.creer(eventId, telephone, lignes, maintenant, delaiPaiement);
        Order commande = orderRepository.save(creee.commande());

        reserverLesPlaces(commande);
        return new Order.CommandeCreee(commande, creee.jeton());
    }

    /**
     * Réserve chaque catégorie, dans l'ordre croissant de categoryId (ordre stable,
     * comme l'émission). Les places réservées sont rendues si la transaction ne se
     * valide pas, quelle qu'en soit la cause.
     */
    private void reserverLesPlaces(Order commande) {
        List<UUID> reservees = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int statut) {
                if (statut == STATUS_COMMITTED) {
                    return;
                }
                for (UUID categoryId : reservees) {
                    try {
                        placesEnVente.liberer(categoryId, commande.getId());
                    } catch (RuntimeException e) {
                        LOG.warn("Réservation non rendue après l'échec de la commande {} (catégorie {}) : "
                            + "elle expirera d'elle-même", commande.getId(), categoryId, e);
                    }
                }
            }
        });

        List<OrderLine> parCategorie = commande.getLignes().stream()
            .sorted(Comparator.comparing(OrderLine::categoryId))
            .toList();
        for (OrderLine ligne : parCategorie) {
            placesEnVente.reserver(ligne.categoryId(), commande.getId(), ligne.quantite());
            reservees.add(ligne.categoryId());
        }
    }
}
