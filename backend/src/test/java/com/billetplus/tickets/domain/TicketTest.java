package com.billetplus.tickets.domain;

import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.domain.exception.BilletNonValideException;
import com.billetplus.tickets.domain.exception.TicketAlreadyScannedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

    private Ticket ticket() {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void un_billet_fraichement_emis_est_valide_et_non_scanne() {
        Ticket ticket = ticket();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EMIS);
        assertThat(ticket.estValide()).isTrue();
        assertThat(ticket.getScanneLe()).isNull();
    }

    @Test
    void les_horodatages_sont_a_la_microseconde_comme_en_base() {
        Ticket ticket = ticket();
        ticket.marquerCommeScanne();

        assertThat(ticket.getEmisLe().getNano() % 1_000).isZero();
        assertThat(ticket.getScanneLe().getNano() % 1_000).isZero();
    }

    @Test
    void marquer_comme_scanne_change_le_statut_et_horodate() {
        Ticket ticket = ticket();

        ticket.marquerCommeScanne();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SCANNE);
        assertThat(ticket.getScanneLe()).isNotNull();
        assertThat(ticket.estValide()).isFalse();
    }

    @Test
    void refuse_un_second_scan_du_meme_billet_en_conflit_metier_avec_l_heure_du_premier_scan() {
        Ticket ticket = ticket();
        ticket.marquerCommeScanne();
        Instant premierScan = ticket.getScanneLe();

        assertThatThrownBy(ticket::marquerCommeScanne)
            .isInstanceOf(TicketAlreadyScannedException.class)
            .isInstanceOf(BusinessConflictException.class)
            // Le message peut atteindre le client (handler 409) : aucune donnée du billet.
            .hasMessageNotContaining(ticket.getId().toString())
            .satisfies(e -> {
                TicketAlreadyScannedException refus = (TicketAlreadyScannedException) e;
                assertThat(refus.getTicketId()).isEqualTo(ticket.getId());
                assertThat(refus.getPremierScanLe()).isEqualTo(premierScan);
            });
        // Le second scan ne réécrit pas l'heure du premier.
        assertThat(ticket.getScanneLe()).isEqualTo(premierScan);
    }

    @Test
    void refuse_de_scanner_un_billet_annule_en_conflit_metier_sans_modifier_son_etat() {
        Ticket ticket = ticket();
        ticket.annuler();

        assertThatThrownBy(ticket::marquerCommeScanne)
            .isInstanceOf(BilletNonValideException.class)
            .isInstanceOf(BusinessConflictException.class)
            .hasMessageNotContaining(ticket.getId().toString())
            .satisfies(e -> assertThat(((BilletNonValideException) e).getStatus()).isEqualTo(TicketStatus.ANNULE));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ANNULE);
        assertThat(ticket.getScanneLe()).isNull();
    }

    @Test
    void refuse_de_scanner_un_billet_rembourse_en_conflit_metier_sans_modifier_son_etat() {
        Ticket ticket = ticket();
        ticket.marquerRembourse();

        assertThatThrownBy(ticket::marquerCommeScanne)
            .isInstanceOf(BilletNonValideException.class)
            .satisfies(e -> assertThat(((BilletNonValideException) e).getStatus()).isEqualTo(TicketStatus.REMBOURSE));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.REMBOURSE);
        assertThat(ticket.getScanneLe()).isNull();
    }

    @Test
    void refuse_d_annuler_un_billet_deja_scanne() {
        Ticket ticket = ticket();
        ticket.marquerCommeScanne();

        assertThatThrownBy(ticket::annuler)
            .isInstanceOf(IllegalStateException.class);
    }
}
