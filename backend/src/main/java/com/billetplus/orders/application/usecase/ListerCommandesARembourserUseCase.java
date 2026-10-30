package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.Order;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lecture (niveau 1, ADR 0002) — commandes payées sans billet, à rembourser à la
 * main depuis l'espace marchand de la passerelle (décision B, ADR 0006).
 *
 * Ne couvre pas encore les commandes PAYEE d'un événement annulé : elles exigent
 * de lire le statut des événements (port StatutEvenements), à ajouter avec le
 * tableau de bord.
 */
@Service
public class ListerCommandesARembourserUseCase {

    /** Borne de sécurité : au-delà, il faudra paginer. */
    public static final int LIMITE = 500;

    private final OrderRepository orderRepository;

    public ListerCommandesARembourserUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> execute() {
        return orderRepository.findPayeesSansBillet(LIMITE);
    }
}
