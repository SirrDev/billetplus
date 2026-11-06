package com.billetplus.orders.domain;

import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarifEntreeLibreTest {

    private final TarifEntreeLibre tarif = new TarifEntreeLibre(100, 75, 50);

    @Test
    void le_seuil_est_inclus_cent_places_paient_le_petit_tarif() {
        assertThat(tarif.tarifPour(100)).isEqualTo(Money.ofFcfa(75));
    }

    @Test
    void au_dela_du_seuil_le_grand_tarif_s_applique() {
        assertThat(tarif.tarifPour(101)).isEqualTo(Money.ofFcfa(50));
    }

    @Test
    void zero_place_paie_le_petit_tarif() {
        assertThat(tarif.tarifPour(0)).isEqualTo(Money.ofFcfa(75));
    }

    @Test
    void un_nombre_de_places_negatif_est_refuse() {
        assertThatThrownBy(() -> tarif.tarifPour(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void les_valeurs_par_defaut_sont_75_jusqu_a_100_places_puis_50() {
        assertThat(TarifEntreeLibre.PAR_DEFAUT).isEqualTo(new TarifEntreeLibre(100, 75, 50));
    }

    @Test
    void un_seuil_nul_ou_negatif_est_refuse() {
        assertThatThrownBy(() -> new TarifEntreeLibre(0, 75, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TarifEntreeLibre(-5, 75, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_petit_tarif_nul_ou_negatif_est_refuse() {
        assertThatThrownBy(() -> new TarifEntreeLibre(100, 0, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TarifEntreeLibre(100, -1, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_grand_tarif_nul_ou_negatif_est_refuse() {
        assertThatThrownBy(() -> new TarifEntreeLibre(100, 75, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TarifEntreeLibre(100, 75, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
