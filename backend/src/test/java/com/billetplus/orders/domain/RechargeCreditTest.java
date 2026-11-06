package com.billetplus.orders.domain;

import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RechargeCreditTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-01T10:00:00.123456789Z");
    private static final NumeroTelephone TELEPHONE = NumeroTelephone.depuis("771234567");
    private static final UUID ORGANISATEUR = UUID.randomUUID();
    private static final long MIN = 1000;
    private static final long MAX = 1_000_000;

    private static RechargeCredit creer(long montant) {
        return RechargeCredit.creer(ORGANISATEUR, montant, TELEPHONE, MIN, MAX, MAINTENANT);
    }

    private static Paiement paiement(String reference) {
        return new Paiement(MoyenPaiement.WAVE, reference, 7, "https://pay.example.com/r", MAINTENANT);
    }

    private static RechargeCredit enAttente() {
        return creer(5000);
    }

    @Test
    void une_recharge_creee_attend_son_paiement() {
        RechargeCredit recharge = creer(5000);

        assertThat(recharge.getId()).isNotNull();
        assertThat(recharge.getOrganisateurId()).isEqualTo(ORGANISATEUR);
        assertThat(recharge.getMontant()).isEqualTo(Money.ofFcfa(5000));
        assertThat(recharge.getTelephone()).isEqualTo(TELEPHONE);
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.EN_ATTENTE);
        assertThat(recharge.getPaiement()).isNull();
        assertThat(recharge.getMontantRecuFcfa()).isNull();
        assertThat(recharge.getTraiteeLe()).isNull();
        assertThat(recharge.getCreeLe()).isEqualTo(Instant.parse("2026-11-01T10:00:00.123456Z"));
    }

    @Test
    void la_borne_basse_est_incluse() {
        assertThat(creer(MIN).getMontant()).isEqualTo(Money.ofFcfa(MIN));
    }

    @Test
    void la_borne_haute_est_incluse() {
        assertThat(creer(MAX).getMontant()).isEqualTo(Money.ofFcfa(MAX));
    }

    @Test
    void un_montant_sous_la_borne_basse_est_refuse() {
        assertThatThrownBy(() -> creer(MIN - 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_montant_au_dessus_de_la_borne_haute_est_refuse() {
        assertThatThrownBy(() -> creer(MAX + 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_montant_negatif_ou_nul_est_refuse() {
        assertThatThrownBy(() -> creer(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> creer(-5000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void l_organisateur_est_obligatoire() {
        assertThatThrownBy(() -> RechargeCredit.creer(null, 5000, TELEPHONE, MIN, MAX, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void le_telephone_est_obligatoire() {
        assertThatThrownBy(() -> RechargeCredit.creer(ORGANISATEUR, 5000, null, MIN, MAX, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void la_date_de_creation_est_obligatoire() {
        assertThatThrownBy(() -> RechargeCredit.creer(ORGANISATEUR, 5000, TELEPHONE, MIN, MAX, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void le_paiement_s_ouvre_une_seule_fois() {
        RechargeCredit recharge = enAttente();
        recharge.ouvrirPaiement(paiement("ref_1"));

        assertThat(recharge.getPaiement().reference()).isEqualTo("ref_1");
        assertThatThrownBy(() -> recharge.ouvrirPaiement(paiement("ref_2")))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(recharge.getPaiement().reference()).isEqualTo("ref_1");
    }

    @Test
    void un_paiement_absent_est_refuse() {
        assertThatThrownBy(() -> enAttente().ouvrirPaiement(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ouvrir_un_paiement_sur_une_recharge_traitee_est_refuse() {
        RechargeCredit recharge = enAttente();
        recharge.echouer(MAINTENANT);

        assertThatThrownBy(() -> recharge.ouvrirPaiement(paiement("ref_1")))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(recharge.getPaiement()).isNull();
    }

    @Test
    void un_montant_egal_passe_en_payee_et_demande_le_credit() {
        RechargeCredit recharge = creer(5000);

        boolean aCrediter = recharge.confirmer(5000, MAINTENANT.plusSeconds(5));

        assertThat(aCrediter).isTrue();
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.PAYEE);
        assertThat(recharge.getMontantRecuFcfa()).isEqualTo(5000L);
        assertThat(recharge.getTraiteeLe()).isEqualTo(Instant.parse("2026-11-01T10:00:05.123456Z"));
    }

    @Test
    void un_montant_inferieur_passe_en_anomalie_sans_credit_et_garde_le_montant_recu() {
        RechargeCredit recharge = creer(5000);

        boolean aCrediter = recharge.confirmer(100, MAINTENANT);

        assertThat(aCrediter).isFalse();
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.ANOMALIE);
        assertThat(recharge.getMontantRecuFcfa()).isEqualTo(100L);
    }

    @Test
    void un_montant_superieur_passe_aussi_en_anomalie() {
        RechargeCredit recharge = creer(5000);

        assertThat(recharge.confirmer(5001, MAINTENANT)).isFalse();
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.ANOMALIE);
        assertThat(recharge.getMontantRecuFcfa()).isEqualTo(5001L);
    }

    @Test
    void echouer_passe_en_echouee() {
        RechargeCredit recharge = enAttente();

        recharge.echouer(MAINTENANT.plusSeconds(1));

        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.ECHOUEE);
        assertThat(recharge.getTraiteeLe()).isNotNull();
        assertThat(recharge.getMontantRecuFcfa()).isNull();
    }

    @Test
    void confirmer_deux_fois_est_refuse_pas_de_double_credit() {
        RechargeCredit recharge = enAttente();
        recharge.confirmer(5000, MAINTENANT);

        assertThatThrownBy(() -> recharge.confirmer(5000, MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.PAYEE);
    }

    @Test
    void echouer_apres_paiement_est_refuse() {
        RechargeCredit recharge = enAttente();
        recharge.confirmer(5000, MAINTENANT);

        assertThatThrownBy(() -> recharge.echouer(MAINTENANT)).isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.PAYEE);
    }

    @Test
    void confirmer_apres_echec_est_refuse() {
        RechargeCredit recharge = enAttente();
        recharge.echouer(MAINTENANT);

        assertThatThrownBy(() -> recharge.confirmer(5000, MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.ECHOUEE);
    }

    @Test
    void confirmer_ou_echouer_depuis_une_anomalie_est_refuse() {
        RechargeCredit recharge = enAttente();
        recharge.confirmer(1, MAINTENANT);

        assertThatThrownBy(() -> recharge.confirmer(5000, MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThatThrownBy(() -> recharge.echouer(MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.ANOMALIE);
        assertThat(recharge.getMontantRecuFcfa()).isEqualTo(1L);
    }
}
