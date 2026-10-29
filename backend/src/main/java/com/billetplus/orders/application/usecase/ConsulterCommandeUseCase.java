package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.Order;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — lecture d'une commande par son
 * acheteur, sans compte : le jeton d'accès remis à la création fait office
 * d'identité (ADR 0006).
 *
 * Commande inconnue, jeton absent ou faux : même exception, même message. La
 * réponse ne permet pas de savoir si un identifiant de commande existe.
 */
@Service
public class ConsulterCommandeUseCase {

    private final OrderRepository orderRepository;

    public ConsulterCommandeUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** @throws NoSuchElementException (404) si la commande est inconnue ou le jeton faux */
    public Order execute(UUID orderId, String jeton) {
        if (orderId == null) {
            throw new NoSuchElementException("Commande introuvable");
        }
        return orderRepository.findById(orderId)
            .filter(commande -> commande.accepteLeJeton(jeton))
            .orElseThrow(() -> new NoSuchElementException("Commande introuvable"));
    }
}
