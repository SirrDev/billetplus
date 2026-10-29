package com.billetplus.orders.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumeroTelephoneTest {

    @ParameterizedTest
    @ValueSource(strings = {"771234567", "77 123 45 67", "+221 77 123 45 67", "+221771234567",
        "00221771234567", "221771234567", "77.123.45.67", "77-123-45-67", " (+221) 77 123 45 67 "})
    void toutes_les_ecritures_d_un_meme_mobile_donnent_le_format_international(String saisie) {
        assertThat(NumeroTelephone.depuis(saisie).valeur()).isEqualTo("+221771234567");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "33 823 45 67", "+221338234567", "7712345", "7712345678",
        "+33612345678", "77123456a", "+221 77 123 45 67 8", "2217712345"})
    void un_numero_qui_n_est_pas_un_mobile_senegalais_est_refuse(String saisie) {
        assertThatThrownBy(() -> NumeroTelephone.depuis(saisie))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deux_ecritures_du_meme_numero_sont_egales() {
        assertThat(NumeroTelephone.depuis("77 123 45 67"))
            .isEqualTo(NumeroTelephone.depuis("00221771234567"))
            .hasSameHashCodeAs(NumeroTelephone.depuis("+221771234567"));
        assertThat(NumeroTelephone.depuis("771234567")).isNotEqualTo(NumeroTelephone.depuis("781234567"));
    }
}
