package com.billetplus.auth.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La clé de développement des pièces est recopiée dans application.yml, ici et dans
 * VerificationProduction (shared ne peut pas importer auth). Si elles divergeaient, la
 * production accepterait la clé publique de développement (ADR 0012).
 */
class CleDeDevPiecesTest {

    @Test
    void la_cle_de_dev_d_application_yml_est_celle_de_l_adaptateur() throws IOException {
        try (InputStream yml = getClass().getResourceAsStream("/application.yml")) {
            assertThat(yml).isNotNull();
            assertThat(new String(yml.readAllBytes(), StandardCharsets.UTF_8))
                .contains("${BILLETPLUS_CLE_PIECES:" + AesGcmChiffrementPieces.CLE_DE_DEV + "}");
        }
    }
}
