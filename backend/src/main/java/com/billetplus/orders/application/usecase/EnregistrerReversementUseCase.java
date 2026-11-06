package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.Reversement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Enregistre un virement fait à la main par l'administrateur vers l'organisateur d'un
 * événement (ADR 0009). Billet+ ne déclenche aucun virement : il garde seulement la trace.
 * L'administrateur vient du jeton, jamais du corps de la requête.
 */
@Service
public class EnregistrerReversementUseCase {

    private final OrderRepository orderRepository;
    private final ReversementRepository reversementRepository;
    private final Clock clock;

    public EnregistrerReversementUseCase(OrderRepository orderRepository, ReversementRepository reversementRepository,
                                         Clock clock) {
        this.orderRepository = orderRepository;
        this.reversementRepository = reversementRepository;
        this.clock = clock;
    }

    /**
     * @throws NoSuchElementException   (404) événement sans aucune commande payée ni reversement
     * @throws IllegalArgumentException (400) montant, référence, motif ou date invalides
     */
    @Transactional
    public Reversement execute(UUID eventId, long montantFcfa, String reference, String motif, LocalDate reverseLe,
                               UUID administrateurId) {
        boolean connu = orderRepository.evenementsAvecCommandesPayees().contains(eventId)
            || reversementRepository.totalReverse(eventId) != 0;
        if (!connu) {
            throw new NoSuchElementException("Aucune vente pour cet événement : " + eventId);
        }
        Reversement reversement = Reversement.enregistrer(eventId, montantFcfa, reference, motif, reverseLe,
            administrateurId, Instant.now(clock));
        return reversementRepository.save(reversement);
    }
}
