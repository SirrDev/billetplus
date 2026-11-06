package com.billetplus.orders.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.RecettesEvenement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Lecture (niveau 1, ADR 0002) — recettes d'un événement vues par son organisateur :
 * brut, commission de Billet+, net, déjà reversé, reste à reverser (ADR 0009). Totaux
 * calculés en base (aucune commande chargée en mémoire). Réservé à l'organisateur :
 * sinon, même 404 qu'un événement inconnu.
 */
@Service
public class ConsulterRecettesUseCase {

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
        OrderRepository.TotauxPayees totaux = orderRepository.totauxPayees(eventId);
        return RecettesEvenement.depuisTotaux(totaux.commandes(), totaux.brutFcfa(), totaux.commissionFcfa(),
            statutEvenements.estAnnule(eventId), reversementRepository.totalReverse(eventId));
    }
}
