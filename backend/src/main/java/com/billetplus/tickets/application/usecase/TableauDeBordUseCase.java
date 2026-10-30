package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.tickets.application.port.SeatReservationStore;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Lecture (niveau 1, ADR 0002) — tableau de bord de l'organisateur : par
 * catégorie, places vendues, en cours de paiement, disponibles, billets scannés,
 * et chiffre d'affaires.
 *
 * Chiffre d'affaires = billets valables (émis ou scannés) × prix de la catégorie.
 * Le prix d'une catégorie ne change jamais : c'est exactement le prix payé. Les
 * billets annulés ou remboursés n'y comptent pas. Montant brut : la commission de
 * la passerelle n'est pas déduite (son taux n'est pas connu ici).
 *
 * Réservé à l'organisateur de l'événement : sinon, même 404 qu'un événement
 * inconnu (règle du module, voir CreateTicketCategoryUseCase).
 */
@Service
public class TableauDeBordUseCase {

    /** Une catégorie du tableau de bord. */
    public record LigneCategorie(UUID categoryId, String nom, long prixFcfa, int quotaTotal, int vendus,
                                 int enCoursDePaiement, int disponibles, long scannes, long annules,
                                 long chiffreAffairesFcfa) {
    }

    public record TableauDeBord(UUID eventId, List<LigneCategorie> categories, int vendus, int enCoursDePaiement,
                                long scannes, long chiffreAffairesFcfa) {
    }

    private final StatutEvenements statutEvenements;
    private final TicketCategoryRepository categoryRepository;
    private final TicketRepository ticketRepository;
    private final SeatReservationStore reservationStore;
    private final Clock clock;

    public TableauDeBordUseCase(StatutEvenements statutEvenements, TicketCategoryRepository categoryRepository,
                                TicketRepository ticketRepository, SeatReservationStore reservationStore, Clock clock) {
        this.statutEvenements = statutEvenements;
        this.categoryRepository = categoryRepository;
        this.ticketRepository = ticketRepository;
        this.reservationStore = reservationStore;
        this.clock = clock;
    }

    /** @throws NoSuchElementException (404) événement inconnu ou dont le demandeur n'est pas l'organisateur */
    public TableauDeBord execute(UUID eventId, UUID organisateurId) {
        if (!statutEvenements.estOrganisateur(eventId, organisateurId)) {
            throw new NoSuchElementException("Événement introuvable : " + eventId);
        }
        Map<UUID, Map<TicketStatus, Long>> comptes = new HashMap<>();
        for (TicketRepository.CompteBillets compte : ticketRepository.compterParCategorieEtStatut(eventId)) {
            comptes.computeIfAbsent(compte.categoryId(), c -> new EnumMap<>(TicketStatus.class))
                .put(compte.statut(), compte.nombre());
        }
        Instant maintenant = Instant.now(clock);

        List<LigneCategorie> lignes = categoryRepository.findByEventId(eventId).stream()
            .sorted(Comparator.comparing(TicketCategory::getNom).thenComparing(TicketCategory::getId))
            .map(categorie -> ligne(categorie, comptes.getOrDefault(categorie.getId(), Map.of()), maintenant))
            .toList();
        return new TableauDeBord(eventId, lignes,
            lignes.stream().mapToInt(LigneCategorie::vendus).sum(),
            lignes.stream().mapToInt(LigneCategorie::enCoursDePaiement).sum(),
            lignes.stream().mapToLong(LigneCategorie::scannes).sum(),
            lignes.stream().mapToLong(LigneCategorie::chiffreAffairesFcfa).sum());
    }

    private LigneCategorie ligne(TicketCategory categorie, Map<TicketStatus, Long> comptes, Instant maintenant) {
        long prix = categorie.getPrix().valeur().longValueExact();
        long emis = comptes.getOrDefault(TicketStatus.EMIS, 0L);
        long scannes = comptes.getOrDefault(TicketStatus.SCANNE, 0L);
        long annules = comptes.getOrDefault(TicketStatus.ANNULE, 0L) + comptes.getOrDefault(TicketStatus.REMBOURSE, 0L);
        int enCours = reservationStore.placesReservees(categorie.getId(), maintenant);
        // Jamais négatif : une réservation de trop (risque résiduel connu) ne doit pas
        // afficher un nombre de places disponibles inférieur à zéro.
        int disponibles = Math.max(0, categorie.quotaRestant() - enCours);
        return new LigneCategorie(categorie.getId(), categorie.getNom(), prix, categorie.getQuotaTotal(),
            categorie.getQuotaVendu(), enCours, disponibles, scannes, annules, (emis + scannes) * prix);
    }
}
