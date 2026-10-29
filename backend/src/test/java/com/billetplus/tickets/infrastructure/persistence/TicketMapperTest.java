package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketMapperTest {

    private Ticket billet() {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void un_aller_retour_conserve_un_billet_emis_sans_date_de_scan() {
        Ticket origine = billet();

        Ticket relu = TicketMapper.versDomaine(TicketMapper.versNouvelleEntite(origine));

        assertThat(relu).usingRecursiveComparison().isEqualTo(origine);
        assertThat(relu.getScanneLe()).isNull();
    }

    @Test
    void un_aller_retour_conserve_un_billet_scanne_et_sa_date_de_scan() {
        Ticket origine = billet();
        origine.marquerCommeScanne();

        Ticket relu = TicketMapper.versDomaine(TicketMapper.versNouvelleEntite(origine));

        assertThat(relu).usingRecursiveComparison().isEqualTo(origine);
    }

    @Test
    void une_nouvelle_entite_n_a_pas_de_version_pour_etre_inseree_et_non_fusionnee() {
        assertThat(TicketMapper.versNouvelleEntite(billet()).getVersion()).isNull();
    }

    @Test
    void copier_dans_reporte_le_statut_et_la_date_de_scan() {
        Ticket ticket = billet();
        TicketJpaEntity entity = TicketMapper.versNouvelleEntite(ticket);

        ticket.marquerCommeScanne();
        TicketMapper.copierDans(ticket, entity);

        assertThat(entity.getStatus()).isEqualTo(TicketStatus.SCANNE);
        assertThat(entity.getScanneLe()).isEqualTo(ticket.getScanneLe());
    }

    @Test
    void copier_dans_refuse_une_entite_d_un_autre_billet() {
        TicketJpaEntity autre = TicketMapper.versNouvelleEntite(billet());

        assertThatThrownBy(() -> TicketMapper.copierDans(billet(), autre))
            .isInstanceOf(IllegalStateException.class);
    }
}
