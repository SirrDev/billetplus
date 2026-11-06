package com.billetplus.orders.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.RecettesEvenement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Lecture (niveau 1, ADR 0002) — recettes d'un événement vues par son organisateur :
 * brut, commission de Billet+, net, déjà reversé, reste à reverser (ADR 0009).
 * Réservé à l'organisateur : sinon, même 404 qu'un événement inconnu.
 */
@Service
public class ConsulterRecettesUseCase {

    /**
     * Borne de lecture des commandes payées d'un événement. Atteinte, la somme serait
     * fausse : refus explicite (500) plutôt qu'un montant tronqué. Il faudra alors une
     * agrégation en base.
     */
    public static final int LIMITE_COMMANDES = 100_000;

    private final OrderRepository orderRepository;
    private final ReversementRepository reversementRepository;
    private final StatutEvenements statutEvenements;

    public ConsulterRecettesUseCase(OrderRepository orderRepository, ReversementRepository reversementRepository,
                                    StatutEvenements statutEvenements) {
        this.orderRepository = orderRepository;
        this.reversementRepository = reversementRepository;
        this.statutEvenements = statutEvenements;
    }

    /** @throws NoSuchElementException (404) événement inconnu ou dont le demandeur n'est pas l'organisateur */
    @Transactional(readOnly = true)
    public RecettesEvenement execute(UUID eventId, UUID organisateurId) {
        if (!statutEvenements.estOrganisateur(eventId, organisateurId)) {
            throw new NoSuchElementException("Événement introuvable : " + eventId);
        }
        return recettesDe(eventId);
    }

    /** Recettes d'un événement, sans contrôle de propriété : pour les autres cas d'usage du module. */
    @Transactional(readOnly = true)
    public RecettesEvenement recettesDe(UUID eventId) {
        List<Order> payees = orderRepository.findPayeesDeLEvenement(eventId, LIMITE_COMMANDES);
        if (payees.size() >= LIMITE_COMMANDES) {
            throw new IllegalStateException("Trop de commandes pour calculer les recettes en mémoire : " + eventId);
        }
        return RecettesEvenement.calculer(payees, statutEvenements.estAnnule(eventId),
            reversementRepository.totalReverse(eventId));
    }
}
