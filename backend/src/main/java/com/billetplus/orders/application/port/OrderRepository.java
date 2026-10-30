package com.billetplus.orders.application.port;

import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;

import java.time.Instant;
import java.util.List;
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

    /**
     * Commandes de ce numéro pour cet événement encore en attente de paiement et
     * dans leur délai à {@code maintenant} (limite par téléphone, ADR 0006).
     */
    long compterEnAttente(NumeroTelephone telephone, UUID eventId, Instant maintenant);

    /** Commande dont le paiement porte cette référence de passerelle (unique, V9). */
    Optional<Order> findByReferencePaiement(String reference);

    /** Commandes PAYEE_SANS_BILLET, à rembourser à la main (décision B), les plus anciennes d'abord. */
    List<Order> findPayeesSansBillet(int limite);

    /** Événements qui ont au moins une commande PAYEE. */
    List<UUID> evenementsAvecCommandesPayees();

    /** Commandes PAYEE d'un événement, les plus anciennes d'abord. */
    List<Order> findPayeesDeLEvenement(UUID eventId, int limite);
}
