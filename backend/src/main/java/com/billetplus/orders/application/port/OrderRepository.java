package com.billetplus.orders.application.port;

import com.billetplus.orders.domain.Order;

import java.util.Optional;
import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — persistance des commandes. L'implémentation
 * JPA vit dans infrastructure/persistence/.
 *
 * Verrouillage optimiste : deux confirmations simultanées de la même commande
 * (webhook Wave rejoué, expiration concurrente) ne peuvent pas toutes deux être
 * validées, à condition que {@link #findById(UUID)}, la transition du domaine et
 * {@link #save(Order)} s'exécutent dans la MÊME transaction, celle du cas d'usage.
 */
public interface OrderRepository {

    Optional<Order> findById(UUID id);

    /** Exige une transaction ouverte par l'appelant (voir la Javadoc de l'interface). */
    Order save(Order order);
}
