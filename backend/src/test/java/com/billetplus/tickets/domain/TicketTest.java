package com.billetplus.tickets.domain;

import com.billetplus.tickets.domain.exception.TicketAlreadyScannedException;
import org.junit.jupiter.api.Test;

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
    void refuse_un_second_scan_du_meme_billet() {
        Ticket ticket = ticket();
        ticket.marquerCommeScanne();

        assertThatThrownBy(ticket::marquerCommeScanne)
            .isInstanceOf(TicketAlreadyScannedException.class);
    }

    @Test
    void refuse_de_scanner_un_billet_annule() {
        Ticket ticket = ticket();
        ticket.annuler();

        assertThatThrownBy(ticket::marquerCommeScanne)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refuse_d_annuler_un_billet_deja_scanne() {
        Ticket ticket = ticket();
        ticket.marquerCommeScanne();

        assertThatThrownBy(ticket::annuler)
            .isInstanceOf(IllegalStateException.class);
    }
}
