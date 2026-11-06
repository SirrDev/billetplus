package com.billetplus.orders.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.RecettesEvenement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture (niveau 1, ADR 0002) — reversements à faire, vus par l'administrateur
 * (ADR 0009) : un événement par ligne, s'il a des commandes payées ou des reversements.
 * Le plus grand reste à reverser d'abord ; un reste négatif (trop reversé) reste visible.
 */
@Service
public class ListerReversementsUseCase {

    /** Une ligne de la liste : l'événement, son statut d'annulation et ses recettes. */
    public record LigneReversement(UUID eventId, boolean evenementAnnule, RecettesEvenement recettes) {
    }

    private final OrderRepository orderRepository;
    private final ReversementRepository reversementRepository;
    private final StatutEvenements statutEvenements;
    private final ConsulterRecettesUseCase consulterRecettes;

    public ListerReversementsUseCase(OrderRepository orderRepository, ReversementRepository reversementRepository,
                                     StatutEvenements statutEvenements, ConsulterRecettesUseCase consulterRecettes) {
        this.orderRepository = orderRepository;
        this.reversementRepository = reversementRepository;
        this.statutEvenements = statutEvenements;
        this.consulterRecettes = consulterRecettes;
    }

    @Transactional(readOnly = true)
    public List<LigneReversement> execute() {
        Set<UUID> evenements = new LinkedHashSet<>(orderRepository.evenementsAvecCommandesPayees());
        evenements.addAll(reversementRepository.totauxParEvenement().keySet());
        List<LigneReversement> lignes = new ArrayList<>();
        for (UUID eventId : evenements) {
            lignes.add(new LigneReversement(eventId, statutEvenements.estAnnule(eventId),
                consulterRecettes.recettesDe(eventId)));
        }
        lignes.sort(Comparator.comparingLong((LigneReversement l) -> l.recettes().resteAReverserFcfa()).reversed()
            .thenComparing(LigneReversement::eventId));
        return lignes;
    }
}
