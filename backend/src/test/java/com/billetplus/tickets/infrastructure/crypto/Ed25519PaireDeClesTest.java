package com.billetplus.tickets.infrastructure.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ed25519PaireDeClesTest {

    private static KeyPair nouvellePaire() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void une_cle_publique_et_sa_cle_privee_sont_acceptees() throws Exception {
        KeyPair paire = nouvellePaire();
        assertThatCode(() -> Ed25519Config.verifierLaPaire(paire.getPrivate(), paire.getPublic()))
            .doesNotThrowAnyException();
    }

    @Test
    void deux_cles_de_paires_differentes_empechent_le_demarrage() throws Exception {
        KeyPair production = nouvellePaire();
        KeyPair dev = nouvellePaire();

        assertThatThrownBy(() -> Ed25519Config.verifierLaPaire(production.getPrivate(), dev.getPublic()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("QR_PUBLIC_KEY_PATH");
    }
}
