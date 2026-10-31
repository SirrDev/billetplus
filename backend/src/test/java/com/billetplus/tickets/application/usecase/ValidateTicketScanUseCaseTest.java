package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.QrVerificationResult;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.application.usecase.ResultatScan.Issue;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidateTicketScanUseCaseTest {

    private static final String QR = "{\"tid\":\"...\"}.signature";
    /** Émis à la MICROseconde, comme en base ; le QR n'en porte que la milliseconde. */
    private static final Instant EMIS_LE = Instant.parse("2026-10-29T10:00:00.123456Z");
    private static final Instant IAT_DU_QR = Instant.parse("2026-10-29T10:00:00.123Z");

    @Mock
    private QRSigner qrSigner;
    @Mock
    private TicketRepository ticketRepository;

    private final UUID eventId = UUID.randomUUID();

    private ValidateTicketScanUseCase useCase() {
        return new ValidateTicketScanUseCase(qrSigner, ticketRepository);
    }

    private Ticket billet(TicketStatus status, Instant scanneLe) {
        return Ticket.reconstituer(UUID.randomUUID(), eventId, UUID.randomUUID(), UUID.randomUUID(),
            status, EMIS_LE, scanneLe);
    }

    /** Le QR vérifié porte exactement les données du billet (iat à la milliseconde). */
    private void qrAuthentiquePour(Ticket ticket) {
        when(qrSigner.verify(QR)).thenReturn(QrVerificationResult.valide(ticket.getId(), ticket.getEventId(), IAT_DU_QR));
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    }

    @Test
    void accepte_un_billet_emis_le_marque_scanne_et_l_enregistre() {
        Ticket ticket = billet(TicketStatus.EMIS, null);
        qrAuthentiquePour(ticket);

        ResultatScan resultat = useCase().execute(QR, eventId);

        assertThat(resultat).isEqualTo(ResultatScan.accepte(ticket.getId()));
        ArgumentCaptor<Ticket> enregistre = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(enregistre.capture());
        assertThat(enregistre.getValue().getStatus()).isEqualTo(TicketStatus.SCANNE);
        assertThat(enregistre.getValue().getScanneLe()).isNotNull();
    }

    /**
     * Le QR ne signe emisLe qu'à la milliseconde : sans troncature de la date en
     * base (microseconde), tout billet légitime serait refusé comme incohérent.
     */
    @Test
    void compare_la_date_d_emission_a_la_milliseconde() {
        Ticket ticket = billet(TicketStatus.EMIS, null);
        qrAuthentiquePour(ticket);

        assertThat(useCase().execute(QR, eventId).issue()).isEqualTo(Issue.ACCEPTE);
    }

    @Test
    void signale_un_billet_deja_scanne_avec_l_heure_du_premier_passage_sans_rien_enregistrer() {
        Instant premierPassage = Instant.parse("2026-10-29T20:03:00Z");
        Ticket ticket = billet(TicketStatus.SCANNE, premierPassage);
        qrAuthentiquePour(ticket);

        ResultatScan resultat = useCase().execute(QR, eventId);

        assertThat(resultat).isEqualTo(ResultatScan.dejaScanne(ticket.getId(), premierPassage));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void signale_un_billet_annule_ou_rembourse_avec_son_statut_sans_rien_enregistrer() {
        for (TicketStatus status : new TicketStatus[]{TicketStatus.ANNULE, TicketStatus.REMBOURSE}) {
            Ticket ticket = billet(status, null);
            qrAuthentiquePour(ticket);

            assertThat(useCase().execute(QR, eventId)).isEqualTo(ResultatScan.nonValide(ticket.getId(), status));
        }
        verify(ticketRepository, never()).save(any());
    }

    /** Un billet authentique pour le concert A ne fait pas entrer au concert B. */
    @Test
    void refuse_un_billet_authentique_d_un_autre_evenement_sans_le_marquer() {
        Ticket ticket = billet(TicketStatus.EMIS, null);
        qrAuthentiquePour(ticket);

        ResultatScan resultat = useCase().execute(QR, UUID.randomUUID());

        assertThat(resultat).isEqualTo(ResultatScan.autreEvenement(ticket.getId()));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EMIS);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void refuse_un_qr_dont_la_signature_est_invalide_sans_lire_la_base() {
        when(qrSigner.verify(QR)).thenReturn(QrVerificationResult.invalide());

        assertThat(useCase().execute(QR, eventId)).isEqualTo(ResultatScan.invalide());
        verifyNoInteractions(ticketRepository);
    }

    /** Ni signature à vérifier, ni base à lire : refusé d'emblée. */
    @Test
    void refuse_un_qr_absent_vide_ou_trop_long_sans_meme_verifier_la_signature() {
        String tropLong = "x".repeat(ValidateTicketScanUseCase.TAILLE_MAX_QR + 1);

        for (String contenu : new String[]{null, "", "   ", tropLong}) {
            assertThat(useCase().execute(contenu, eventId)).isEqualTo(ResultatScan.invalide());
        }
        verifyNoInteractions(qrSigner, ticketRepository);
    }

    @Test
    void refuse_un_qr_authentique_pour_un_billet_inconnu() {
        UUID inconnu = UUID.randomUUID();
        when(qrSigner.verify(QR)).thenReturn(QrVerificationResult.valide(inconnu, eventId, IAT_DU_QR));
        when(ticketRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThat(useCase().execute(QR, eventId)).isEqualTo(ResultatScan.invalide());
        verify(ticketRepository, never()).save(any());
    }

    /** Signature authentique sur des données fausses (clé compromise ?) : refusé, sans détail. */
    @Test
    void refuse_un_qr_authentique_mais_incoherent_avec_la_base() {
        Ticket ticket = billet(TicketStatus.EMIS, null);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        when(qrSigner.verify(QR)).thenReturn(QrVerificationResult.valide(ticket.getId(), UUID.randomUUID(), IAT_DU_QR));
        assertThat(useCase().execute(QR, eventId)).isEqualTo(ResultatScan.invalide());

        when(qrSigner.verify(QR)).thenReturn(QrVerificationResult.valide(ticket.getId(), eventId, IAT_DU_QR.plusMillis(1)));
        assertThat(useCase().execute(QR, eventId)).isEqualTo(ResultatScan.invalide());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EMIS);
        verify(ticketRepository, never()).save(any());
    }

    /** ADR 0008 : après un transfert, le QR resté chez l'expéditeur est refusé, le nouveau accepté. */
    @Test
    void refuse_le_qr_d_avant_le_transfert_et_accepte_le_nouveau() {
        Ticket ticket = billet(TicketStatus.EMIS, null);
        ticket.transfererA("+221781234567", "a".repeat(64));
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        when(qrSigner.verify(QR)).thenReturn(QrVerificationResult.valide(ticket.getId(), eventId, IAT_DU_QR));
        assertThat(useCase().execute(QR, eventId)).isEqualTo(ResultatScan.invalide());
        verify(ticketRepository, never()).save(any());

        Instant nouvelIat = ticket.getQrSigneLe().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        when(qrSigner.verify(QR)).thenReturn(QrVerificationResult.valide(ticket.getId(), eventId, nouvelIat));
        assertThat(useCase().execute(QR, eventId).issue()).isEqualTo(Issue.ACCEPTE);
    }

    /** Toutes les causes d'invalidité donnent exactement la même réponse : aucun indice pour un faussaire. */
    @Test
    void un_qr_invalide_ne_porte_aucune_donnee_quelle_qu_en_soit_la_cause() {
        ResultatScan invalide = ResultatScan.invalide();

        assertThat(invalide.ticketId()).isNull();
        assertThat(invalide.premierScanLe()).isNull();
        assertThat(invalide.statut()).isNull();
        assertThat(useCase().execute(null, eventId)).isSameAs(invalide);
    }

    @Test
    void exige_l_evenement_controle() {
        assertThatThrownBy(() -> useCase().execute(QR, null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(qrSigner, ticketRepository);
    }
}
