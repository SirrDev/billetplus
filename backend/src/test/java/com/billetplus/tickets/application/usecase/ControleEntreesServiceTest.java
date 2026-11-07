package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.application.port.TicketRepository.CompteBillets;
import com.billetplus.tickets.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControleEntreesServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Test
    void additionne_seulement_les_billets_scannes() {
        UUID eventId = UUID.randomUUID();
        UUID cat1 = UUID.randomUUID();
        UUID cat2 = UUID.randomUUID();
        when(ticketRepository.compterParCategorieEtStatut(eventId)).thenReturn(List.of(
            new CompteBillets(cat1, TicketStatus.SCANNE, 3),
            new CompteBillets(cat2, TicketStatus.SCANNE, 4),
            new CompteBillets(cat1, TicketStatus.EMIS, 100),
            new CompteBillets(cat1, TicketStatus.ANNULE, 50),
            new CompteBillets(cat2, TicketStatus.REMBOURSE, 20)));

        assertThat(new ControleEntreesService(ticketRepository).billetsScannes(eventId)).isEqualTo(7);
    }

    @Test
    void zero_quand_aucun_billet_n_est_scanne() {
        UUID eventId = UUID.randomUUID();
        when(ticketRepository.compterParCategorieEtStatut(eventId)).thenReturn(List.of(
            new CompteBillets(UUID.randomUUID(), TicketStatus.EMIS, 10)));

        assertThat(new ControleEntreesService(ticketRepository).billetsScannes(eventId)).isZero();
    }

    @Test
    void zero_quand_l_evenement_n_a_aucun_billet() {
        UUID eventId = UUID.randomUUID();
        when(ticketRepository.compterParCategorieEtStatut(eventId)).thenReturn(List.of());

        assertThat(new ControleEntreesService(ticketRepository).billetsScannes(eventId)).isZero();
    }

    @Test
    void zero_pour_un_identifiant_null_sans_appeler_le_depot() {
        assertThat(new ControleEntreesService(ticketRepository).billetsScannes(null)).isZero();

        verifyNoInteractions(ticketRepository);
    }
}
