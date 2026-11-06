package com.billetplus.orders.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.CreditRepository;
import com.billetplus.orders.application.port.DelaiAttenteVerrous;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.TarifEntreeLibre;
import com.billetplus.shared.Money;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.api.publique.EmissionBillets;
import com.billetplus.tickets.api.publique.PlacesEnVente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — entrée libre (ADR 0010) : une commande
 * entièrement gratuite est confirmée sans passerelle, et l'organisateur paie chaque
 * billet sur son crédit prépayé.
 *
 * UNE transaction : création et réservation des places (CreateOrderUseCase), plafond
 * par numéro, tarif lu à cet instant et figé dans la commande, débit atomique du
 * crédit, émission des billets. Le moindre échec annule tout : ni commande, ni débit,
 * ni billet, et les places réservées sont rendues après l'annulation.
 */
@Service
public class CreerEntreeLibreUseCase {

    /** Le solde de l'organisateur n'est jamais révélé à l'acheteur. */
    static final String CREDIT_INSUFFISANT =
        "Les entrées gratuites de cet événement sont momentanément épuisées.";
    private static final Duration DELAI_VERROUS = Duration.ofSeconds(5);

    private final CreateOrderUseCase createOrder;
    private final OrderRepository orderRepository;
    private final CreditRepository creditRepository;
    private final PlacesEnVente placesEnVente;
    private final EmissionBillets emissionBillets;
    private final StatutEvenements statutEvenements;
    private final DelaiAttenteVerrous delaiAttenteVerrous;
    private final Clock clock;
    private final TarifEntreeLibre tarif;
    private final int maxBilletsParTelephone;

    public CreerEntreeLibreUseCase(CreateOrderUseCase createOrder, OrderRepository orderRepository,
                                   CreditRepository creditRepository, PlacesEnVente placesEnVente,
                                   EmissionBillets emissionBillets, StatutEvenements statutEvenements,
                                   DelaiAttenteVerrous delaiAttenteVerrous, Clock clock,
                                   @Value("${billetplus.entree-libre.seuil-places:100}") int seuilPlaces,
                                   @Value("${billetplus.entree-libre.tarif-petit:75}") int tarifPetit,
                                   @Value("${billetplus.entree-libre.tarif-grand:50}") int tarifGrand,
                                   @Value("${billetplus.entree-libre.max-billets-par-telephone:10}") int maxBilletsParTelephone) {
        // Configuration invalide : refus au démarrage, pas au premier billet gratuit.
        this.tarif = new TarifEntreeLibre(seuilPlaces, tarifPetit, tarifGrand);
        if (maxBilletsParTelephone < 1) {
            throw new IllegalStateException("billetplus.entree-libre.max-billets-par-telephone doit être au moins 1");
        }
        this.createOrder = createOrder;
        this.orderRepository = orderRepository;
        this.creditRepository = creditRepository;
        this.placesEnVente = placesEnVente;
        this.emissionBillets = emissionBillets;
        this.statutEvenements = statutEvenements;
        this.delaiAttenteVerrous = delaiAttenteVerrous;
        this.clock = clock;
        this.maxBilletsParTelephone = maxBilletsParTelephone;
    }

    /**
     * @return la commande PAYEE, billets émis, et son jeton d'accès en clair (ADR 0006)
     * @throws IllegalArgumentException   (400) paramètre invalide, ou une catégorie payante
     * @throws NoSuchElementException     (404) catégorie inconnue ou événement fermé à la vente
     * @throws BusinessConflictException  (409) places insuffisantes, plafond par numéro atteint,
     *                                    ou crédit de l'organisateur insuffisant
     */
    @Transactional
    public Order.CommandeCreee execute(UUID eventId, String telephoneSaisi,
                                       List<CreateOrderUseCase.LigneDemandee> demandees) {
        // Attente bornée sur la ligne du crédit et sur les quotas : au-delà, 503 à relancer.
        delaiAttenteVerrous.appliquer(DELAI_VERROUS);
        Order.CommandeCreee creee = createOrder.creerPourEntreeLibre(eventId, telephoneSaisi, demandees);
        Order commande = creee.commande();

        NumeroTelephone telephone = commande.getTelephone();
        // Avant le comptage : deux demandes simultanées du même numéro passent l'une après l'autre.
        creditRepository.verrouillerEntreesLibres(telephone.valeur(), eventId);
        long dejaObtenus = orderRepository.compterBilletsGratuits(telephone, eventId);
        if (dejaObtenus + commande.nombreBillets() > maxBilletsParTelephone) {
            throw new BusinessConflictException(
                "Un numéro peut obtenir au plus %d billets gratuits pour cet événement".formatted(maxBilletsParTelephone));
        }

        UUID organisateur = statutEvenements.organisateurDe(eventId)
            .orElseThrow(() -> new NoSuchElementException("Événement introuvable"));
        Money tarifParBillet = tarif.tarifPour(placesEnVente.placesTotales(eventId));

        Instant maintenant = Instant.now(clock);
        commande.confirmerEntreeLibre(tarifParBillet, maintenant);
        Order confirmee = orderRepository.save(commande);

        if (!creditRepository.debiterEntreeLibre(organisateur, confirmee.fraisEntreeLibre(), confirmee.getId(),
            maintenant)) {
            throw new BusinessConflictException(CREDIT_INSUFFISANT);
        }

        confirmee.getLignes().stream()
            .sorted(Comparator.comparing(OrderLine::categoryId))
            .forEach(ligne -> emissionBillets.emettre(ligne.categoryId(), confirmee.getId(), ligne.quantite()));
        return new Order.CommandeCreee(confirmee, creee.jeton());
    }
}
