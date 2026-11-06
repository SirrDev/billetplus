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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lecture (niveau 1, ADR 0002) — reversements à faire, vus par l'administrateur
 * (ADR 0009) : un événement par ligne, s'il a des commandes payées ou des reversements.
 * Deux requêtes agrégées (commandes, reversements) pour toute la liste, puis le statut de
 * chaque événement. Le plus grand reste à reverser d'abord ; un reste négatif (trop
 * reversé) reste visible.
 */
@Service
public class ListerReversementsUseCase {

    /** Une ligne de la liste : l'événement, son statut d'annulation et ses recettes. */
    public record LigneReversement(UUID eventId, boolean evenementAnnule, RecettesEvenement recettes) {
    }

    private final OrderRepository orderRepository;
    private final ReversementRepository reversementRepository;
    private final StatutEvenements statutEvenements;

    public ListerReversementsUseCase(OrderRepository orderRepository, ReversementRepository reversementRepository,
                                     StatutEvenements statutEvenements) {
        this.orderRepository = orderRepository;
        this.reversementRepository = reversementRepository;
        this.statutEvenements = statutEvenements;
    }

    @Transactional(readOnly = true)
    public List<LigneReversement> execute() {
        Map<UUID, OrderRepository.TotauxPayees> ventes = orderRepository.totauxPayeesParEvenement().stream()
            .collect(Collectors.toMap(OrderRepository.TotauxPayees::eventId, Function.identity()));
        Map<UUID, Long> reverses = reversementRepository.totauxParEvenement();
        Set<UUID> evenements = new LinkedHashSet<>(ventes.keySet());
        evenements.addAll(reverses.keySet());

        List<LigneReversement> lignes = new ArrayList<>();
        for (UUID eventId : evenements) {
            OrderRepository.TotauxPayees totaux = ventes.getOrDefault(eventId,
                new OrderRepository.TotauxPayees(eventId, 0, 0, 0));
            boolean annule = statutEvenements.estAnnule(eventId);
            lignes.add(new LigneReversement(eventId, annule, RecettesEvenement.depuisTotaux(totaux.commandes(),
                totaux.brutFcfa(), totaux.commissionFcfa(), annule, reverses.getOrDefault(eventId, 0L))));
        }
        lignes.sort(Comparator.comparingLong((LigneReversement l) -> l.recettes().resteAReverserFcfa()).reversed()
            .thenComparing(LigneReversement::eventId));
        return lignes;
    }
}
