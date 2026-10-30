package com.billetplus.orders.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lecture (niveau 1, ADR 0002) — commandes à rembourser à la main depuis l'espace
 * marchand de la passerelle (décision B, ADR 0006) :
 * - les commandes PAYEE_SANS_BILLET, avec leur motif ;
 * - les commandes PAYEE d'un événement annulé (motif {@value #EVENEMENT_ANNULE}) :
 *   leurs billets sont annulés (ADR 0005), l'argent est à rendre.
 *
 * Le statut des événements est lu par le port public StatutEvenements (ADR 0005).
 */
@Service
public class ListerCommandesARembourserUseCase {

    /** Borne de sécurité : au-delà, il faudra paginer. */
    public static final int LIMITE = 500;
    public static final String EVENEMENT_ANNULE = "EVENEMENT_ANNULE";

    /** Une commande à rembourser, et pourquoi. */
    public record ARembourser(Order commande, String motif) {
    }

    private final OrderRepository orderRepository;
    private final StatutEvenements statutEvenements;

    public ListerCommandesARembourserUseCase(OrderRepository orderRepository, StatutEvenements statutEvenements) {
        this.orderRepository = orderRepository;
        this.statutEvenements = statutEvenements;
    }

    public List<ARembourser> execute() {
        List<ARembourser> resultat = new ArrayList<>();
        for (Order commande : orderRepository.findPayeesSansBillet(LIMITE)) {
            resultat.add(new ARembourser(commande, commande.getMotifSansBillet().name()));
        }
        for (UUID evenement : orderRepository.evenementsAvecCommandesPayees()) {
            if (resultat.size() >= LIMITE) {
                break;
            }
            if (statutEvenements.estAnnule(evenement)) {
                for (Order commande : orderRepository.findPayeesDeLEvenement(evenement, LIMITE - resultat.size())) {
                    resultat.add(new ARembourser(commande, EVENEMENT_ANNULE));
                }
            }
        }
        return resultat;
    }
}
