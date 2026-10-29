package com.billetplus.orders.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JetonAccesTest {

    @Test
    void un_jeton_fait_256_bits_en_base64_url_et_son_empreinte_64_caracteres_hexadecimaux() {
        JetonAcces jeton = JetonAcces.generer();

        assertThat(jeton.enClair()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(jeton.empreinte()).hasSize(64).matches("[0-9a-f]+");
        assertThat(jeton.empreinte()).isEqualTo(JetonAcces.empreinteDe(jeton.enClair()));
    }

    @Test
    void l_empreinte_est_le_sha256_du_jeton() {
        // Vecteur connu : SHA-256("abc").
        assertThat(JetonAcces.empreinteDe("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void les_jetons_generes_sont_tous_differents() {
        Set<String> jetons = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            jetons.add(JetonAcces.generer().enClair());
        }
        assertThat(jetons).hasSize(1000);
    }

    @Test
    void seul_le_bon_jeton_correspond_a_l_empreinte() {
        JetonAcces jeton = JetonAcces.generer();

        assertThat(JetonAcces.correspond(jeton.enClair(), jeton.empreinte())).isTrue();
        assertThat(JetonAcces.correspond(JetonAcces.generer().enClair(), jeton.empreinte())).isFalse();
        assertThat(JetonAcces.correspond(null, jeton.empreinte())).isFalse();
        assertThat(JetonAcces.correspond(jeton.enClair(), null)).isFalse();
        assertThat(JetonAcces.correspond(jeton.enClair(), "zz".repeat(32))).isFalse();
        assertThat(JetonAcces.correspond(jeton.enClair(), "abc")).isFalse();
        // L'empreinte elle-même n'est pas un jeton valable.
        assertThat(JetonAcces.correspond(jeton.empreinte(), jeton.empreinte())).isFalse();
    }

    @Test
    void le_toString_ne_divulgue_pas_le_jeton() {
        JetonAcces jeton = JetonAcces.generer();
        assertThat(jeton.toString()).doesNotContain(jeton.enClair()).doesNotContain(jeton.empreinte());
    }
}
