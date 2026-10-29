package com.billetplus.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void multiplier_un_montant_par_une_quantite() {
        assertThat(Money.ofFcfa(5000).fois(3)).isEqualTo(Money.ofFcfa(15000));
        assertThat(Money.ofFcfa(5000).fois(0)).isEqualTo(Money.ofFcfa(0));
    }

    @Test
    void une_quantite_negative_est_refusee() {
        assertThatThrownBy(() -> Money.ofFcfa(5000).fois(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addition_soustraction_et_montant_negatif_refuse() {
        assertThat(Money.ofFcfa(100).plus(Money.ofFcfa(50))).isEqualTo(Money.ofFcfa(150));
        assertThat(Money.ofFcfa(100).moins(Money.ofFcfa(100))).isEqualTo(Money.ofFcfa(0));
        assertThatThrownBy(() -> Money.ofFcfa(100).moins(Money.ofFcfa(101)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.ofFcfa(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
