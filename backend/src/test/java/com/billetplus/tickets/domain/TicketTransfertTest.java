package com.billetplus.tickets.domain;

import com.billetplus.tickets.domain.exception.BilletDejaTransfereException;
import com.billetplus.tickets.domain.exception.BilletNonValideException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ticket#transfererA et le jeton du destinataire (ADR 0008). */
class TicketTransfertTest {

    private static final String DESTINATAIRE = "+221781234567";

    private Ticket billet() {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void un_billet_neuf_signe_son_qr_a_sa_date_d_emission() {
        Ticket ticket = billet();
        assertThat(ticket.getQrSigneLe()).isEqualTo(ticket.getEmisLe());
        assertThat(ticket.estTransfere()).isFalse();
    }

    @Test
    void le_transfert_change_la_date_du_qr_a_la_milliseconde() {
        Ticket ticket = billet();
        JetonTransfert jeton = JetonTransfert.generer();

        ticket.transfererA(DESTINATAIRE, jeton.empreinte());

        assertThat(ticket.estTransfere()).isTrue();
        assertThat(ticket.getTransfereA()).isEqualTo(DESTINATAIRE);
        assertThat(ticket.getEmpreinteJetonTransfert()).isEqualTo(jeton.empreinte());
        // Le QR ne signe qu'à la milliseconde : c'est à cette précision que les dates doivent différer.
        assertThat(ticket.getQrSigneLe().truncatedTo(ChronoUnit.MILLIS))
            .isNotEqualTo(ticket.getEmisLe().truncatedTo(ChronoUnit.MILLIS));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EMIS);
    }

    @Test
    void le_transfert_meme_dans_la_milliseconde_de_l_emission_change_le_qr() {
        // Émis « maintenant » à la microseconde : le transfert immédiat tombe souvent dans la même milliseconde.
        Instant emisLe = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Ticket ticket = Ticket.reconstituer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            TicketStatus.EMIS, emisLe, null);

        ticket.transfererA(DESTINATAIRE, JetonTransfert.generer().empreinte());

        assertThat(ticket.getQrSigneLe().truncatedTo(ChronoUnit.MILLIS)).isAfter(emisLe.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void le_transfert_est_definitif() {
        Ticket ticket = billet();
        ticket.transfererA(DESTINATAIRE, JetonTransfert.generer().empreinte());
        Instant qr = ticket.getQrSigneLe();

        assertThatThrownBy(() -> ticket.transfererA("+221771234567", JetonTransfert.generer().empreinte()))
            .isInstanceOf(BilletDejaTransfereException.class);
        assertThat(ticket.getTransfereA()).isEqualTo(DESTINATAIRE);
        assertThat(ticket.getQrSigneLe()).isEqualTo(qr);
    }

    @Test
    void un_billet_scanne_annule_ou_rembourse_ne_se_transfere_pas() {
        for (TicketStatus statut : new TicketStatus[] {TicketStatus.SCANNE, TicketStatus.ANNULE, TicketStatus.REMBOURSE}) {
            Ticket ticket = Ticket.reconstituer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                statut, Instant.now(), statut == TicketStatus.SCANNE ? Instant.now() : null);
            assertThatThrownBy(() -> ticket.transfererA(DESTINATAIRE, JetonTransfert.generer().empreinte()))
                .isInstanceOf(BilletNonValideException.class);
            assertThat(ticket.estTransfere()).isFalse();
        }
    }

    @Test
    void refuse_un_numero_non_normalise_ou_une_empreinte_mal_formee() {
        Ticket ticket = billet();
        assertThatThrownBy(() -> ticket.transfererA("781234567", JetonTransfert.generer().empreinte()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ticket.transfererA(DESTINATAIRE, "pas-une-empreinte"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(ticket.estTransfere()).isFalse();
    }

    @Test
    void seul_le_bon_numero_avec_le_bon_jeton_ouvre_le_billet() {
        Ticket ticket = billet();
        JetonTransfert jeton = JetonTransfert.generer();
        assertThat(ticket.appartientAuDestinataire(DESTINATAIRE, jeton.enClair())).isFalse();

        ticket.transfererA(DESTINATAIRE, jeton.empreinte());

        assertThat(ticket.appartientAuDestinataire(DESTINATAIRE, jeton.enClair())).isTrue();
        assertThat(ticket.appartientAuDestinataire("+221771234567", jeton.enClair())).isFalse();
        assertThat(ticket.appartientAuDestinataire(DESTINATAIRE, JetonTransfert.generer().enClair())).isFalse();
        assertThat(ticket.appartientAuDestinataire(DESTINATAIRE, null)).isFalse();
        assertThat(ticket.appartientAuDestinataire(null, jeton.enClair())).isFalse();
    }

    @Test
    void le_jeton_ne_se_divulgue_pas_et_son_empreinte_est_stable() {
        JetonTransfert jeton = JetonTransfert.generer();
        assertThat(jeton.enClair()).hasSize(43);
        assertThat(jeton.empreinte()).hasSize(64).isEqualTo(JetonTransfert.empreinteDe(jeton.enClair()));
        assertThat(jeton.toString()).doesNotContain(jeton.enClair());
    }
}
