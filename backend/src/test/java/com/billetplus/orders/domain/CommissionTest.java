package com.billetplus.orders.domain;

import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Commission, net et reversements (ADR 0009), sans Spring ni base. */
class CommissionTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-06T10:00:00Z");
    private static final NumeroTelephone TELEPHONE = NumeroTelephone.depuis("77 123 45 67");

    private static Order commande(long prixUnitaire, int quantite, TauxCommission taux) {
        return Order.creer(UUID.randomUUID(), TELEPHONE,
            List.of(new OrderLine(UUID.randomUUID(), quantite, Money.ofFcfa(prixUnitaire))),
            MAINTENANT, Duration.ofMinutes(4), taux).commande();
    }

    private static Order payee(long prixUnitaire, int quantite, TauxCommission taux) {
        Order commande = commande(prixUnitaire, quantite, taux);
        commande.marquerPayee(MAINTENANT.plusSeconds(60));
        return commande;
    }

    // ---------------------------------------------------------------- taux

    @Test
    void cinq_pour_cent_arrondis_au_franc_inferieur() {
        assertThat(TauxCommission.CINQ_POUR_CENT.commissionSur(Money.ofFcfa(3333))).isEqualTo(Money.ofFcfa(166));
        assertThat(TauxCommission.CINQ_POUR_CENT.commissionSur(Money.ofFcfa(10_000))).isEqualTo(Money.ofFcfa(500));
        assertThat(TauxCommission.CINQ_POUR_CENT.commissionSur(Money.ofFcfa(19))).isEqualTo(Money.ofFcfa(0));
        assertThat(TauxCommission.CINQ_POUR_CENT.commissionSur(Money.ofFcfa(0))).isEqualTo(Money.ofFcfa(0));
        assertThat(new TauxCommission(0).commissionSur(Money.ofFcfa(5000))).isEqualTo(Money.ofFcfa(0));
        assertThat(new TauxCommission(10_000).commissionSur(Money.ofFcfa(5000))).isEqualTo(Money.ofFcfa(5000));
    }

    @Test
    void un_taux_hors_bornes_est_refuse() {
        assertThatThrownBy(() -> new TauxCommission(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TauxCommission(10_001)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- commande

    @Test
    void la_commande_garde_son_taux_et_calcule_commission_et_net() {
        Order commande = commande(3333, 1, TauxCommission.CINQ_POUR_CENT);

        assertThat(commande.getTauxCommission()).isEqualTo(TauxCommission.CINQ_POUR_CENT);
        assertThat(commande.commission()).isEqualTo(Money.ofFcfa(166));
        assertThat(commande.montantNet()).isEqualTo(Money.ofFcfa(3167));
    }

    @Test
    void une_commande_gratuite_ne_paie_aucune_commission() {
        Order gratuite = commande(0, 2, TauxCommission.CINQ_POUR_CENT);
        assertThat(gratuite.commission()).isEqualTo(Money.ofFcfa(0));
        assertThat(gratuite.montantNet()).isEqualTo(Money.ofFcfa(0));
    }

    @Test
    void une_commande_sans_taux_est_refusee() {
        assertThatThrownBy(() -> Order.creer(UUID.randomUUID(), TELEPHONE,
                List.of(new OrderLine(UUID.randomUUID(), 1, Money.ofFcfa(1000))), MAINTENANT, Duration.ofMinutes(4), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- recettes

    @Test
    void recettes_calculees_commande_par_commande_avec_le_taux_de_chacune() {
        Order aCinq = payee(3333, 1, TauxCommission.CINQ_POUR_CENT);     // commission 166
        Order aTrois = payee(10_000, 2, new TauxCommission(300));       // commission 600
        Order enAttente = commande(50_000, 1, TauxCommission.CINQ_POUR_CENT);

        RecettesEvenement recettes = RecettesEvenement.calculer(List.of(aCinq, aTrois, enAttente), false, 10_000);

        assertThat(recettes.commandes()).isEqualTo(2);
        assertThat(recettes.brut()).isEqualTo(Money.ofFcfa(23_333));
        assertThat(recettes.commission()).isEqualTo(Money.ofFcfa(766));
        assertThat(recettes.net()).isEqualTo(Money.ofFcfa(22_567));
        assertThat(recettes.reverseFcfa()).isEqualTo(10_000);
        assertThat(recettes.resteAReverserFcfa()).isEqualTo(12_567);
    }

    @Test
    void un_evenement_annule_ne_donne_ni_commission_ni_net_et_montre_le_trop_reverse() {
        Order payeeAvantAnnulation = payee(10_000, 1, TauxCommission.CINQ_POUR_CENT);

        RecettesEvenement recettes = RecettesEvenement.calculer(List.of(payeeAvantAnnulation), true, 9_500);

        assertThat(recettes.commandes()).isZero();
        assertThat(recettes.brut()).isEqualTo(Money.ofFcfa(0));
        assertThat(recettes.commission()).isEqualTo(Money.ofFcfa(0));
        assertThat(recettes.net()).isEqualTo(Money.ofFcfa(0));
        assertThat(recettes.resteAReverserFcfa()).isEqualTo(-9_500);
    }

    @Test
    void une_commande_payee_sans_billet_ne_compte_pas() {
        Order sansBillet = commande(10_000, 1, TauxCommission.CINQ_POUR_CENT);
        sansBillet.marquerPayeeSansBillet(MotifSansBillet.PLACES_INDISPONIBLES, MAINTENANT.plusSeconds(60));

        RecettesEvenement recettes = RecettesEvenement.calculer(List.of(sansBillet), false, 0);

        assertThat(recettes.commandes()).isZero();
        assertThat(recettes.net()).isEqualTo(Money.ofFcfa(0));
    }

    // ---------------------------------------------------------------- reversement

    @Test
    void un_reversement_valide_est_nettoye() {
        Reversement r = Reversement.enregistrer(UUID.randomUUID(), 95_000, "  WAVE-123  ", "   ",
            LocalDate.of(2026, 11, 6), UUID.randomUUID(), MAINTENANT);

        assertThat(r.getId()).isNotNull();
        assertThat(r.getReference()).isEqualTo("WAVE-123");
        assertThat(r.getMotif()).isNull();
        assertThat(r.getMontantFcfa()).isEqualTo(95_000);
    }

    @Test
    void une_correction_negative_doit_etre_motivee() {
        UUID evenement = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        LocalDate jour = LocalDate.of(2026, 11, 6);

        assertThatThrownBy(() -> Reversement.enregistrer(evenement, -5_000, "CORR-1", null, jour, admin, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("motivée");
        assertThat(Reversement.enregistrer(evenement, -5_000, "CORR-1", "Doublon du virement WAVE-123", jour, admin,
            MAINTENANT).getMontantFcfa()).isEqualTo(-5_000);
    }

    @Test
    void un_reversement_invalide_est_refuse() {
        UUID evenement = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        LocalDate jour = LocalDate.of(2026, 11, 6);

        assertThatThrownBy(() -> Reversement.enregistrer(evenement, 0, "R", null, jour, admin, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reversement.enregistrer(evenement, 1_000, " ", null, jour, admin, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reversement.enregistrer(evenement, 1_000, "R".repeat(101), null, jour, admin, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reversement.enregistrer(evenement, 1_000, "R", null, jour.plusDays(1), admin, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("futur");
        assertThatThrownBy(() -> Reversement.enregistrer(evenement, Reversement.MONTANT_MAX_FCFA + 1, "R", null, jour,
                admin, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reversement.enregistrer(null, 1_000, "R", null, jour, admin, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reversement.enregistrer(evenement, 1_000, "R", null, jour, null, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
