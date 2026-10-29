package com.billetplus.tickets.application.usecase;

import com.billetplus.shared.Money;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.SeatReservationStore;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketIssuanceRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.QuantiteParCommande;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;
import com.billetplus.tickets.domain.exception.QuotaDepasseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueTicketUseCaseTest {

    @Mock
    private TicketCategoryRepository categoryRepository;
    @Mock
    private TicketIssuanceRepository issuanceRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private QRSigner qrSigner;
    @Mock
    private SeatReservationStore reservationStore;

    private IssueTicketUseCase useCase() {
        return new IssueTicketUseCase(categoryRepository, issuanceRepository, ticketRepository, qrSigner, reservationStore);
    }

    /**
     * execute() est @Transactional : en production, une transaction est toujours
     * active. Ici, sans Spring, la synchronisation est activée à la main ; les
     * tests déclenchent eux-mêmes la « validation » avec {@link #validerLaTransaction()}.
     */
    @BeforeEach
    void activerLaSynchronisationDesTransactions() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void nettoyerLaSynchronisationDesTransactions() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void validerLaTransaction() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private TicketCategory categorie(int quotaTotal) {
        return new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), quotaTotal);
    }

    @Test
    void emet_les_billets_demandes_consomme_le_quota_et_signe_chaque_qr() {
        TicketCategory category = categorie(10);
        UUID orderId = UUID.randomUUID();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn("payload.signature");

        List<IssuedTicket> emis = useCase().execute(category.getId(), orderId, 3);

        verify(issuanceRepository).enregistrer(orderId, category.getId(), 3);

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

    /**
     * L'ordre est la garantie d'idempotence : l'émission est enregistrée avant
     * que le quota ne soit touché, pour qu'un appel concurrent pour la même
     * commande soit bloqué par la clé primaire avant toute réservation.
     */
    @Test
    void enregistre_l_emission_avant_de_reserver_le_quota_et_de_creer_les_billets() {
        TicketCategory category = categorie(10);
        UUID orderId = UUID.randomUUID();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase().execute(category.getId(), orderId, 2);

        InOrder ordre = inOrder(categoryRepository, issuanceRepository, ticketRepository);
        ordre.verify(categoryRepository).findById(category.getId());
        ordre.verify(issuanceRepository).enregistrer(orderId, category.getId(), 2);
        ordre.verify(categoryRepository).save(category);
        ordre.verify(ticketRepository, times(2)).save(any(Ticket.class));
    }

    /** Les places sont vendues : la réservation temporaire est libérée, mais seulement une fois la vente validée. */
    @Test
    void libere_la_reservation_temporaire_seulement_apres_la_validation_de_la_transaction() {
        TicketCategory category = categorie(10);
        UUID orderId = UUID.randomUUID();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase().execute(category.getId(), orderId, 2);

        verifyNoInteractions(reservationStore);   // pas avant la validation
        validerLaTransaction();
        verify(reservationStore).liberer(category.getId(), orderId);
    }

    /** Une panne Redis après la validation n'annule pas une vente déjà validée : la réservation expirera d'elle-même. */
    @Test
    void une_panne_a_la_liberation_n_echoue_pas_l_emission_deja_validee() {
        TicketCategory category = categorie(10);
        UUID orderId = UUID.randomUUID();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("Redis indisponible")).when(reservationStore).liberer(category.getId(), orderId);

        List<IssuedTicket> emis = useCase().execute(category.getId(), orderId, 2);

        assertThatCode(IssueTicketUseCaseTest::validerLaTransaction).doesNotThrowAnyException();
        assertThat(emis).hasSize(2);
    }

    @Test
    void ne_libere_aucune_reservation_quand_l_emission_echoue() {
        TicketCategory category = categorie(2);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> useCase().execute(category.getId(), UUID.randomUUID(), 3))
            .isInstanceOf(QuotaDepasseException.class);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(reservationStore);
    }

    @Test
    void refuse_d_emettre_pour_une_categorie_inexistante() {
        UUID categoryIdInconnue = UUID.randomUUID();
        when(categoryRepository.findById(categoryIdInconnue)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(categoryIdInconnue, UUID.randomUUID(), 1))
            .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(issuanceRepository, ticketRepository, qrSigner);
    }

    /**
     * L'émission a déjà été enregistrée quand le quota se révèle insuffisant :
     * l'exception annule la transaction, registre compris (vérifié en
     * intégration, TicketPersistenceIntegrationTest).
     */
    @Test
    void refuse_d_emettre_au_dela_du_quota_restant_sans_reserver_ni_creer_de_billet() {
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
        doThrow(new BilletsDejaEmisException(orderId, category.getId()))
            .when(issuanceRepository).enregistrer(orderId, category.getId(), 1);

        assertThatThrownBy(() -> useCase().execute(category.getId(), orderId, 1))
            .isInstanceOf(BilletsDejaEmisException.class)
            .isInstanceOf(BusinessConflictException.class);

        assertThat(category.getQuotaVendu()).isZero();
        verify(categoryRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(qrSigner);
    }

    /**
     * Refusée avant toute lecture : la quantité est écrite dans le registre
     * d'émission, où une valeur invalide violerait la contrainte CHECK (500).
     */
    @Test
    void refuse_une_quantite_nulle_ou_negative_sans_rien_lire_ni_ecrire() {
        assertThatThrownBy(() -> useCase().execute(UUID.randomUUID(), UUID.randomUUID(), 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().execute(UUID.randomUUID(), UUID.randomUUID(), -1))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(categoryRepository, issuanceRepository, ticketRepository, qrSigner);
    }

    @Test
    void accepte_exactement_le_plafond_de_billets_par_emission() {
        TicketCategory category = categorie(100);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        List<IssuedTicket> emis = useCase().execute(
            category.getId(), UUID.randomUUID(), QuantiteParCommande.MAX);

        assertThat(emis).hasSize(QuantiteParCommande.MAX);
    }

    @Test
    void refuse_une_emission_au_dela_du_plafond_sans_rien_lire_ni_ecrire() {
        UUID categoryId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase().execute(
            categoryId, UUID.randomUUID(), QuantiteParCommande.MAX + 1))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(categoryRepository, issuanceRepository, ticketRepository, qrSigner);
    }

    @Test
    void refuse_une_commande_ou_une_categorie_absente() {
        assertThatThrownBy(() -> useCase().execute(null, UUID.randomUUID(), 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().execute(UUID.randomUUID(), null, 1))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(categoryRepository, issuanceRepository, ticketRepository, qrSigner);
    }
}
