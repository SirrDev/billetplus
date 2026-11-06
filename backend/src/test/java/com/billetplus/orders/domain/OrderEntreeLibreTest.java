package com.billetplus.orders.domain;

import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Entree libre (ADR 0010) : commandes gratuites, confirmation sans passerelle, frais. */
class OrderEntreeLibreTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-01T10:00:00Z");
    private static final Duration DELAI = Duration.ofMinutes(4);
    private static final NumeroTelephone TELEPHONE = NumeroTelephone.depuis("77 123 45 67");

    private static OrderLine ligne(int quantite, long prix) {
        return new OrderLine(UUID.randomUUID(), quantite, Money.ofFcfa(prix));
    }

    private static Order creer(List<OrderLine> lignes) {
        return Order.creer(UUID.randomUUID(), TELEPHONE, lignes, MAINTENANT, DELAI, TauxCommission.CINQ_POUR_CENT)
            .commande();
    }

    @Test
    void une_commande_mixte_gratuit_puis_payant_est_refusee_a_la_creation() {
        assertThatThrownBy(() -> creer(List.of(ligne(1, 0), ligne(1, 5000))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void une_commande_mixte_payant_puis_gratuit_est_refusee_a_la_creation() {
        assertThatThrownBy(() -> creer(List.of(ligne(1, 5000), ligne(1, 0))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void une_commande_de_plusieurs_lignes_toutes_gratuites_est_acceptee() {
        assertThat(creer(List.of(ligne(1, 0), ligne(2, 0))).estGratuite()).isTrue();
    }

    @Test
    void une_commande_payante_n_est_pas_gratuite() {
        assertThat(creer(List.of(ligne(1, 5000))).estGratuite()).isFalse();
    }

    @Test
    void le_nombre_de_billets_additionne_les_quantites_de_toutes_les_lignes() {
        assertThat(creer(List.of(ligne(2, 0), ligne(3, 0), ligne(5, 0))).nombreBillets()).isEqualTo(10);
    }

    @Test
    void les_frais_d_une_commande_payante_sont_nuls() {
        assertThat(creer(List.of(ligne(3, 5000))).fraisEntreeLibre()).isEqualTo(Money.ofFcfa(0));
    }

    @Test
    void les_frais_d_une_commande_gratuite_non_confirmee_sont_nuls() {
        assertThat(creer(List.of(ligne(3, 0))).fraisEntreeLibre()).isEqualTo(Money.ofFcfa(0));
    }

    @Test
    void confirmer_une_entree_libre_paye_la_commande_et_fige_le_tarif() {
        Order commande = creer(List.of(ligne(2, 0), ligne(3, 0)));

        commande.confirmerEntreeLibre(Money.ofFcfa(75), MAINTENANT.plusSeconds(10));

        assertThat(commande.getStatus()).isEqualTo(OrderStatus.PAYEE);
        assertThat(commande.getPayeeLe()).isEqualTo(MAINTENANT.plusSeconds(10));
        assertThat(commande.getTarifEntreeLibre()).isEqualTo(Money.ofFcfa(75));
        assertThat(commande.fraisEntreeLibre()).isEqualTo(Money.ofFcfa(375));
    }

    @Test
    void confirmer_une_commande_payante_est_refuse_et_rien_ne_change() {
        Order commande = creer(List.of(ligne(1, 5000)));

        assertThatThrownBy(() -> commande.confirmerEntreeLibre(Money.ofFcfa(75), MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
        assertThat(commande.getTarifEntreeLibre()).isNull();
    }

    @Test
    void un_tarif_nul_est_refuse_et_la_commande_reste_en_attente() {
        Order commande = creer(List.of(ligne(1, 0)));

        assertThatThrownBy(() -> commande.confirmerEntreeLibre(Money.ofFcfa(0), MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
    }

    @Test
    void un_tarif_absent_est_refuse() {
        Order commande = creer(List.of(ligne(1, 0)));

        assertThatThrownBy(() -> commande.confirmerEntreeLibre(null, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
    }

    @Test
    void confirmer_apres_le_delai_est_refuse_sans_figer_de_tarif() {
        Order commande = creer(List.of(ligne(1, 0)));

        assertThatThrownBy(() -> commande.confirmerEntreeLibre(Money.ofFcfa(75), commande.getExpireLe()))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
        assertThat(commande.getTarifEntreeLibre()).isNull();
    }

    @Test
    void confirmer_une_commande_deja_payee_est_refuse_et_garde_le_premier_tarif() {
        Order commande = creer(List.of(ligne(1, 0)));
        commande.confirmerEntreeLibre(Money.ofFcfa(75), MAINTENANT);

        assertThatThrownBy(() -> commande.confirmerEntreeLibre(Money.ofFcfa(50), MAINTENANT.plusSeconds(1)))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(commande.getTarifEntreeLibre()).isEqualTo(Money.ofFcfa(75));
    }

    @Test
    void confirmer_une_commande_annulee_est_refuse() {
        Order commande = creer(List.of(ligne(1, 0)));
        commande.annuler();

        assertThatThrownBy(() -> commande.confirmerEntreeLibre(Money.ofFcfa(75), MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(commande.getTarifEntreeLibre()).isNull();
    }

    @Test
    void confirmer_une_commande_avec_paiement_ouvert_est_refuse() {
        Order commande = creer(List.of(ligne(1, 0)));
        commande.ouvrirPaiement(new Paiement(MoyenPaiement.WAVE, "ref_1", 1, "https://pay.example.com/1",
            MAINTENANT.plusSeconds(1)), Duration.ofSeconds(10));

        assertThatThrownBy(() -> commande.confirmerEntreeLibre(Money.ofFcfa(75), MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
    }
}
