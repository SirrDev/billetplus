package com.billetplus.shared.limitation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitationDebitPropertiesTest {

    private static LimitationDebitProperties.Regle regle(String nom, String methode, String chemin,
                                                         LimitationDebitProperties.Cle cle, int limite, Duration fenetre) {
        return new LimitationDebitProperties.Regle(nom, methode, chemin, cle, limite, fenetre);
    }

    private static LimitationDebitProperties.Regle valide(String nom) {
        return regle(nom, "post", "/api/orders", LimitationDebitProperties.Cle.IP, 10, Duration.ofMinutes(1));
    }

    @Test
    void une_regle_valide_est_acceptee_et_sa_methode_mise_en_majuscules() {
        assertThat(valide("commande").methode()).isEqualTo("POST");
    }

    @Test
    void une_regle_mal_ecrite_empeche_le_demarrage() {
        Duration minute = Duration.ofMinutes(1);
        LimitationDebitProperties.Cle ip = LimitationDebitProperties.Cle.IP;
        assertThatThrownBy(() -> regle("Nom Invalide", "POST", "/api", ip, 1, minute)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> regle(null, "POST", "/api", ip, 1, minute)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> regle("a", "TRACE", "/api", ip, 1, minute)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> regle("a", "POST", "api", ip, 1, minute)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> regle("a", "POST", "/api", null, 1, minute)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> regle("a", "POST", "/api", ip, 0, minute)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> regle("a", "POST", "/api", ip, 1, Duration.ofMillis(999))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> regle("a", "POST", "/api", ip, 1, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deux_regles_du_meme_nom_sont_refusees() {
        assertThatThrownBy(() -> new LimitationDebitProperties(true, List.of(valide("commande"), valide("commande"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("commande");
    }
}
