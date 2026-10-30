package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.SeatReservationStore;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TableauDeBordUseCaseTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-01T10:00:00Z");

    private final StatutEvenements statut = mock(StatutEvenements.class);
    private final TicketCategoryRepository categories = mock(TicketCategoryRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final SeatReservationStore reservations = mock(SeatReservationStore.class);
    private final TableauDeBordUseCase useCase = new TableauDeBordUseCase(statut, categories, tickets, reservations,
        Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private final UUID evenement = UUID.randomUUID();
    private final UUID organisateur = UUID.randomUUID();

    @Test
    void chaque_categorie_donne_ses_ventes_ses_paiements_en_cours_ses_scans_et_son_chiffre_d_affaires() {
        TicketCategory vip = TicketCategory.reconstituer(UUID.randomUUID(), evenement, "VIP", Money.ofFcfa(15000), 10, 6);
        TicketCategory standard = TicketCategory.reconstituer(UUID.randomUUID(), evenement, "Standard", Money.ofFcfa(5000), 100, 0);
        when(statut.estOrganisateur(evenement, organisateur)).thenReturn(true);
        when(categories.findByEventId(evenement)).thenReturn(List.of(vip, standard));
        when(tickets.compterParCategorieEtStatut(evenement)).thenReturn(List.of(
            new TicketRepository.CompteBillets(vip.getId(), TicketStatus.EMIS, 3),
            new TicketRepository.CompteBillets(vip.getId(), TicketStatus.SCANNE, 2),
            new TicketRepository.CompteBillets(vip.getId(), TicketStatus.ANNULE, 1)));
        when(reservations.placesReservees(eq(vip.getId()), eq(MAINTENANT))).thenReturn(3);
        when(reservations.placesReservees(eq(standard.getId()), any())).thenReturn(0);

        TableauDeBordUseCase.TableauDeBord tableau = useCase.execute(evenement, organisateur);

        // Triées par nom : Standard, puis VIP.
        assertThat(tableau.categories()).extracting(TableauDeBordUseCase.LigneCategorie::nom)
            .containsExactly("Standard", "VIP");
        TableauDeBordUseCase.LigneCategorie ligneVip = tableau.categories().get(1);
        assertThat(ligneVip.vendus()).isEqualTo(6);
        assertThat(ligneVip.enCoursDePaiement()).isEqualTo(3);
        assertThat(ligneVip.disponibles()).isEqualTo(1);
        assertThat(ligneVip.scannes()).isEqualTo(2);
        assertThat(ligneVip.annules()).isEqualTo(1);
        // Billets valables (3 émis + 2 scannés) × 15 000 : le billet annulé ne compte pas.
        assertThat(ligneVip.chiffreAffairesFcfa()).isEqualTo(75000);
        assertThat(tableau.categories().get(0).chiffreAffairesFcfa()).isZero();
        assertThat(tableau.vendus()).isEqualTo(6);
        assertThat(tableau.enCoursDePaiement()).isEqualTo(3);
        assertThat(tableau.chiffreAffairesFcfa()).isEqualTo(75000);
    }

    @Test
    void les_places_disponibles_ne_sont_jamais_negatives() {
        TicketCategory pleine = TicketCategory.reconstituer(UUID.randomUUID(), evenement, "VIP", Money.ofFcfa(15000), 10, 9);
        when(statut.estOrganisateur(evenement, organisateur)).thenReturn(true);
        when(categories.findByEventId(evenement)).thenReturn(List.of(pleine));
        when(tickets.compterParCategorieEtStatut(evenement)).thenReturn(List.of());
        when(reservations.placesReservees(any(), any())).thenReturn(4);

        assertThat(useCase.execute(evenement, organisateur).categories().get(0).disponibles()).isZero();
    }

    @Test
    void un_autre_que_l_organisateur_recoit_la_meme_reponse_qu_un_evenement_inconnu() {
        when(statut.estOrganisateur(evenement, organisateur)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(evenement, organisateur))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Événement introuvable : " + evenement);
        verifyNoInteractions(categories, tickets, reservations);
    }
}
