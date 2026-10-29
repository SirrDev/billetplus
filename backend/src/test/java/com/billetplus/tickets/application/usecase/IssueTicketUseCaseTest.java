package com.billetplus.tickets.application.usecase;

import com.billetplus.shared.Money;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;
import com.billetplus.tickets.domain.exception.QuotaDepasseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueTicketUseCaseTest {

    @Mock
    private TicketCategoryRepository categoryRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private QRSigner qrSigner;

    private IssueTicketUseCase useCase() {
        return new IssueTicketUseCase(categoryRepository, ticketRepository, qrSigner);
    }

    private TicketCategory categorie(int quotaTotal) {
        return new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), quotaTotal);
    }

    @Test
    void emet_les_billets_demandes_consomme_le_quota_et_signe_chaque_qr() {
        TicketCategory category = categorie(10);
        UUID orderId = UUID.randomUUID();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(ticketRepository.existsByOrderIdAndCategoryId(orderId, category.getId())).thenReturn(false);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn("payload.signature");

        List<IssuedTicket> emis = useCase().execute(category.getId(), orderId, 3);

        assertThat(emis).hasSize(3);
        assertThat(emis).allSatisfy(issued -> {
            assertThat(issued.ticket().getEventId()).isEqualTo(category.getEventId());
            assertThat(issued.ticket().getCategoryId()).isEqualTo(category.getId());
            assertThat(issued.ticket().getOrderId()).isEqualTo(orderId);
            assertThat(issued.ticket().getStatus()).isEqualTo(TicketStatus.EMIS);
            assertThat(issued.qrContent()).isEqualTo("payload.signature");
        });
        assertThat(emis).extracting(issued -> issued.ticket().getId()).doesNotHaveDuplicates();
        assertThat(category.getQuotaVendu()).isEqualTo(3);
        verify(categoryRepository).save(category);
        verify(ticketRepository, times(3)).save(any(Ticket.class));
    }

    @Test
    void signe_le_qr_avec_l_identifiant_l_evenement_et_la_date_d_emission_du_billet() {
        TicketCategory category = categorie(10);
        UUID orderId = UUID.randomUUID();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn("payload.signature");

        Ticket ticket = useCase().execute(category.getId(), orderId, 1).get(0).ticket();

        verify(qrSigner).sign(ticket.getId(), ticket.getEventId(), ticket.getEmisLe());
    }

    @Test
    void refuse_d_emettre_pour_une_categorie_inexistante() {
        UUID categoryIdInconnue = UUID.randomUUID();
        when(categoryRepository.findById(categoryIdInconnue)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(categoryIdInconnue, UUID.randomUUID(), 1))
            .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(ticketRepository, qrSigner);
    }

    @Test
    void refuse_d_emettre_au_dela_du_quota_restant_sans_rien_ecrire() {
        TicketCategory category = categorie(2);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> useCase().execute(category.getId(), UUID.randomUUID(), 3))
            .isInstanceOf(QuotaDepasseException.class)
            .isInstanceOf(BusinessConflictException.class);

        assertThat(category.getQuotaVendu()).isZero();
        verify(categoryRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(qrSigner);
    }

    @Test
    void refuse_une_seconde_emission_pour_la_meme_commande_et_la_meme_categorie() {
        TicketCategory category = categorie(10);
        UUID orderId = UUID.randomUUID();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(ticketRepository.existsByOrderIdAndCategoryId(orderId, category.getId())).thenReturn(true);

        assertThatThrownBy(() -> useCase().execute(category.getId(), orderId, 1))
            .isInstanceOf(BilletsDejaEmisException.class)
            .isInstanceOf(BusinessConflictException.class);

        assertThat(category.getQuotaVendu()).isZero();
        verify(categoryRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(qrSigner);
    }

    @Test
    void refuse_une_quantite_nulle_ou_negative() {
        TicketCategory category = categorie(10);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> useCase().execute(category.getId(), UUID.randomUUID(), 0))
            .isInstanceOf(IllegalArgumentException.class);

        verify(categoryRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void refuse_une_commande_ou_une_categorie_absente() {
        assertThatThrownBy(() -> useCase().execute(null, UUID.randomUUID(), 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().execute(UUID.randomUUID(), null, 1))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(categoryRepository, ticketRepository, qrSigner);
    }
}
