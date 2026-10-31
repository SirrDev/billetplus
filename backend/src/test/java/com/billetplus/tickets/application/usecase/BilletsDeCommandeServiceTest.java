package com.billetplus.tickets.application.usecase;

import com.billetplus.shared.Money;
import com.billetplus.tickets.api.publique.BilletVendu;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BilletsDeCommandeServiceTest {

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketCategoryRepository categories = mock(TicketCategoryRepository.class);
    private final QRSigner signataire = mock(QRSigner.class);
    private final BilletsDeCommandeService service = new BilletsDeCommandeService(tickets, categories, signataire);

    private final UUID evenement = UUID.randomUUID();
    private final UUID commande = UUID.randomUUID();
    private final TicketCategory vip = new TicketCategory(evenement, "VIP", Money.ofFcfa(15000), 10);
    private static final Instant EMIS_LE = Instant.parse("2026-11-01T10:00:00.123456Z");

    private Ticket billet(TicketStatus statut) {
        return Ticket.reconstituer(UUID.randomUUID(), evenement, vip.getId(), commande, statut, EMIS_LE,
            statut == TicketStatus.SCANNE ? EMIS_LE.plusSeconds(60) : null);
    }

    @Test
    void seul_un_billet_encore_valable_recoit_son_qr_recalcule() {
        Ticket emis = billet(TicketStatus.EMIS);
        Ticket scanne = billet(TicketStatus.SCANNE);
        Ticket annule = billet(TicketStatus.ANNULE);
        when(tickets.findByOrderId(commande)).thenReturn(List.of(emis, scanne, annule));
        when(categories.findById(vip.getId())).thenReturn(Optional.of(vip));
        when(signataire.sign(emis.getId(), evenement, EMIS_LE)).thenReturn("qr-signe");

        List<BilletVendu> billets = service.billetsDe(commande);

        assertThat(billets).hasSize(3).allSatisfy(b -> assertThat(b.categoryNom()).isEqualTo("VIP"));
        BilletVendu lu = billets.stream().filter(b -> b.id().equals(emis.getId())).findFirst().orElseThrow();
        assertThat(lu.statut()).isEqualTo("EMIS");
        assertThat(lu.qr()).contains("qr-signe");
        assertThat(billets.stream().filter(b -> !b.id().equals(emis.getId())))
            .allSatisfy(b -> assertThat(b.qr()).isEmpty());
        // Une seule signature, avec les données d'émission (même QR qu'à l'origine).
        verify(signataire).sign(emis.getId(), evenement, EMIS_LE);
        verify(signataire, never()).sign(scanne.getId(), evenement, EMIS_LE);
    }

    /**
     * Contrat de BilletVendu avec orders (ADR 0008) : un billet transféré est exposé
     * TRANSFERE, sans QR, avec le mobile masqué, même si le destinataire l'a scanné depuis.
     */
    @Test
    void un_billet_transfere_est_expose_transfere_sans_qr_avec_le_numero_masque() {
        Ticket transfere = billet(TicketStatus.EMIS);
        transfere.transfererA("+221781234567", "a".repeat(64));
        Ticket transfereEtScanne = billet(TicketStatus.EMIS);
        transfereEtScanne.transfererA("+221771234500", "b".repeat(64));
        transfereEtScanne.marquerCommeScanne();
        when(tickets.findByOrderId(commande)).thenReturn(List.of(transfere, transfereEtScanne));
        when(categories.findById(vip.getId())).thenReturn(Optional.of(vip));

        List<BilletVendu> billets = service.billetsDe(commande);

        assertThat(billets).allSatisfy(b -> {
            assertThat(b.statut()).isEqualTo("TRANSFERE");
            assertThat(b.qr()).isEmpty();
        });
        assertThat(billets).extracting(b -> b.transfereA().orElseThrow())
            .containsExactlyInAnyOrder("78 ••• •• 67", "77 ••• •• 00");
        verify(signataire, never()).sign(any(), any(), any());
    }

    @Test
    void les_billets_sont_tries_par_categorie_puis_identifiant() {
        TicketCategory standard = new TicketCategory(evenement, "Standard", Money.ofFcfa(5000), 10);
        Ticket b1 = Ticket.reconstituer(new UUID(0, 2), evenement, new UUID(2, 0), commande, TicketStatus.ANNULE, EMIS_LE, null);
        Ticket b2 = Ticket.reconstituer(new UUID(0, 1), evenement, new UUID(1, 0), commande, TicketStatus.ANNULE, EMIS_LE, null);
        Ticket b3 = Ticket.reconstituer(new UUID(0, 1), evenement, new UUID(2, 0), commande, TicketStatus.ANNULE, EMIS_LE, null);
        when(tickets.findByOrderId(commande)).thenReturn(List.of(b1, b2, b3));
        when(categories.findById(any())).thenReturn(Optional.of(standard));

        assertThat(service.billetsDe(commande)).extracting(BilletVendu::id, BilletVendu::categoryId)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(b2.getId(), b2.getCategoryId()),
                org.assertj.core.groups.Tuple.tuple(b3.getId(), b3.getCategoryId()),
                org.assertj.core.groups.Tuple.tuple(b1.getId(), b1.getCategoryId()));
        // Nom de catégorie lu une fois par catégorie, pas une fois par billet.
        verify(categories, org.mockito.Mockito.times(2)).findById(any());
    }

    @Test
    void une_commande_sans_billet_ou_absente_donne_une_liste_vide() {
        when(tickets.findByOrderId(commande)).thenReturn(List.of());
        assertThat(service.billetsDe(commande)).isEmpty();
        assertThat(service.billetsDe(null)).isEmpty();
    }
}
