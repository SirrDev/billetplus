package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelEventTicketsUseCaseTest {

    @Mock
    private TicketRepository ticketRepository;

    private CancelEventTicketsUseCase useCase() {
        return new CancelEventTicketsUseCase(ticketRepository);
    }

    @Test
    void annule_tous_les_billets_encore_valides_de_l_evenement() {
        UUID eventId = UUID.randomUUID();
        List<Ticket> valides = List.of(
            new Ticket(eventId, UUID.randomUUID(), UUID.randomUUID()),
            new Ticket(eventId, UUID.randomUUID(), UUID.randomUUID()));
        when(ticketRepository.findByEventIdAndStatus(eventId, TicketStatus.EMIS)).thenReturn(valides);

        int annules = useCase().execute(eventId);

        assertThat(annules).isEqualTo(2);
        assertThat(valides).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.ANNULE));
        verify(ticketRepository, times(2)).save(any(Ticket.class));
    }

    /** Ne lit que les billets EMIS : les billets scannés, déjà annulés ou remboursés ne sont jamais touchés. */
    @Test
    void ne_touche_qu_aux_billets_emis_et_ne_fait_rien_s_il_n_y_en_a_plus() {
        UUID eventId = UUID.randomUUID();
        when(ticketRepository.findByEventIdAndStatus(eventId, TicketStatus.EMIS)).thenReturn(List.of());

        assertThat(useCase().execute(eventId)).isZero();

        verify(ticketRepository).findByEventIdAndStatus(eventId, TicketStatus.EMIS);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void exige_l_identifiant_de_l_evenement() {
        assertThatThrownBy(() -> useCase().execute(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ticketRepository);
    }
}
