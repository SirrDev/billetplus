package com.billetplus.tickets.application.usecase;

import com.billetplus.shared.Money;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.application.port.SeatReservationStore;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.domain.ReservationTemporaire;
import com.billetplus.tickets.domain.QuantiteParCommande;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.PlacesIndisponiblesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReserveSeatsUseCaseTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-29T10:00:00Z");
    private static final Duration CINQ_MINUTES = Duration.ofMinutes(5);

    @Mock
    private TicketCategoryRepository categoryRepository;
    @Mock
    private SeatReservationStore reservationStore;

    private ReserveSeatsUseCase useCase() {
        return new ReserveSeatsUseCase(categoryRepository, reservationStore,
            Clock.fixed(MAINTENANT, ZoneOffset.UTC), CINQ_MINUTES);
    }

    /** 100 places, 30 déjà vendues : la limite des réservations est 70. */
    private TicketCategory categorieAvecTrenteVendues() {
        TicketCategory category = new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), 100);
        category.reserver(30);
        return category;
    }

    @Test
    void reserve_avec_pour_limite_les_places_encore_vendables_et_expire_apres_cinq_minutes() {
        TicketCategory category = categorieAvecTrenteVendues();
        UUID orderId = UUID.randomUUID();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(reservationStore.reserver(any(), anyInt(), any()))
            .thenAnswer(inv -> Optional.of(inv.<ReservationTemporaire>getArgument(0).getExpireLe()));

        ReservationTemporaire reservation = useCase().execute(category.getId(), orderId, 4);

        assertThat(reservation.getCategoryId()).isEqualTo(category.getId());
        assertThat(reservation.getOrderId()).isEqualTo(orderId);
        assertThat(reservation.getQuantite()).isEqualTo(4);
        assertThat(reservation.getExpireLe()).isEqualTo(Instant.parse("2026-10-29T10:05:00Z"));
        ArgumentCaptor<ReservationTemporaire> demandee = ArgumentCaptor.forClass(ReservationTemporaire.class);
        verify(reservationStore).reserver(demandee.capture(), eq(70), eq(MAINTENANT));
        assertThat(demandee.getValue().getExpireLe()).isEqualTo(Instant.parse("2026-10-29T10:05:00Z"));
    }

    /**
     * Remplacement d'une réservation existante : le stockage garde l'expiration
     * d'origine, et c'est elle que le cas d'usage renvoie (pas une nouvelle
     * expiration à +5 minutes, qui prolongerait le blocage des places).
     */
    @Test
    void renvoie_l_expiration_effectivement_enregistree_et_non_celle_demandee() {
        TicketCategory category = categorieAvecTrenteVendues();
        Instant expirationDOrigine = Instant.parse("2026-10-29T10:02:00Z");
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(reservationStore.reserver(any(), anyInt(), any())).thenReturn(Optional.of(expirationDOrigine));

        ReservationTemporaire reservation = useCase().execute(category.getId(), UUID.randomUUID(), 2);

        assertThat(reservation.getExpireLe()).isEqualTo(expirationDOrigine);
        assertThat(reservation.getQuantite()).isEqualTo(2);
    }

    @Test
    void refuse_en_409_quand_les_places_disponibles_ne_suffisent_pas() {
        TicketCategory category = categorieAvecTrenteVendues();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(reservationStore.reserver(any(), anyInt(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(category.getId(), UUID.randomUUID(), 4))
            .isInstanceOf(PlacesIndisponiblesException.class)
            .isInstanceOf(BusinessConflictException.class)
            .hasMessageNotContaining("70");   // ne révèle pas le stock restant
    }

    /** Une réservation ne consomme jamais le quota : seule une vente payée le fait. */
    @Test
    void ne_modifie_jamais_la_categorie() {
        TicketCategory category = categorieAvecTrenteVendues();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(reservationStore.reserver(any(), anyInt(), any())).thenReturn(Optional.of(MAINTENANT.plus(CINQ_MINUTES)));

        useCase().execute(category.getId(), UUID.randomUUID(), 4);

        assertThat(category.getQuotaVendu()).isEqualTo(30);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void refuse_de_reserver_pour_une_categorie_inexistante() {
        UUID inconnue = UUID.randomUUID();
        when(categoryRepository.findById(inconnue)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(inconnue, UUID.randomUUID(), 1))
            .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(reservationStore);
    }

    @Test
    void refuse_une_quantite_hors_bornes_sans_rien_lire_ni_ecrire() {
        UUID categoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase().execute(categoryId, orderId, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().execute(categoryId, orderId, QuantiteParCommande.MAX + 1))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(categoryRepository, reservationStore);
    }

    @Test
    void refuse_une_commande_ou_une_categorie_absente() {
        assertThatThrownBy(() -> useCase().execute(null, UUID.randomUUID(), 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().execute(UUID.randomUUID(), null, 1))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(categoryRepository, reservationStore);
    }
}
