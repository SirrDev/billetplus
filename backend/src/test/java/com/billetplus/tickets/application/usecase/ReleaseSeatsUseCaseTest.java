package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.SeatReservationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReleaseSeatsUseCaseTest {

    @Mock
    private SeatReservationStore reservationStore;

    @Test
    void libere_la_reservation_de_la_commande_pour_la_categorie() {
        UUID categoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        new ReleaseSeatsUseCase(reservationStore).execute(categoryId, orderId);

        verify(reservationStore).liberer(categoryId, orderId);
    }

    @Test
    void refuse_une_commande_ou_une_categorie_absente() {
        ReleaseSeatsUseCase useCase = new ReleaseSeatsUseCase(reservationStore);

        assertThatThrownBy(() -> useCase.execute(null, UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(reservationStore);
    }
}
