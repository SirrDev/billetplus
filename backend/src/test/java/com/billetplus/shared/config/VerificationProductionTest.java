package com.billetplus.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationProductionTest {

    private static final String SECRET = "x".repeat(VerificationProduction.SECRET_JWT_OCTETS_MIN);
    private static final String PRIVEE = "file:/run/secrets/ed25519_private.pem";
    private static final String PUBLIQUE = "file:/run/secrets/ed25519_public.pem";
    private static final String CLE = "cle-unitechpay-de-test";

    private static StringRedisTemplate redisAvecPolitique(String politique) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        Properties config = new Properties();
        if (politique != null) {
            config.setProperty("maxmemory-policy", politique);
        }
        when(redis.execute(any(RedisCallback.class))).thenReturn(config);
        return redis;
    }

    /** Clé des pièces propre à la production (ADR 0012), différente de celle d'application.yml. */
    private static final String CLE_PIECES = "Y2xlLWRlcy1waWVjZXMtcG91ci1sZXMtdGVzdHMtISE=";

    /** Même valeur que la clé de dev d'application.yml : sinon la production l'accepterait (ADR 0012). */
    @Test
    void la_cle_de_dev_refusee_est_bien_celle_d_application_yml() throws java.io.IOException {
        try (java.io.InputStream yml = getClass().getResourceAsStream("/application.yml")) {
            assertThat(new String(yml.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("${BILLETPLUS_CLE_PIECES:" + VerificationProduction.CLE_PIECES_DE_DEV + "}");
        }
    }

    @Test
    void la_cle_des_pieces_de_developpement_ou_absente_est_refusee() {
        assertThat(VerificationProduction.verifierClePieces(VerificationProduction.CLE_PIECES_DE_DEV)).hasSize(1);
        assertThat(VerificationProduction.verifierClePieces("")).hasSize(1);
        assertThat(VerificationProduction.verifierClePieces(null)).singleElement()
            .asString().contains("BILLETPLUS_CLE_PIECES").doesNotContain(VerificationProduction.CLE_PIECES_DE_DEV);
        assertThat(VerificationProduction.verifierClePieces(CLE_PIECES)).isEmpty();
    }

    @Test
    void une_configuration_de_production_correcte_est_acceptee() {
        assertThatCode(() -> new VerificationProduction(SECRET, PRIVEE, PUBLIQUE, true, CLE, CLE_PIECES, redisAvecPolitique("noeviction")))
            .doesNotThrowAnyException();
    }

    @Test
    void le_secret_jwt_de_dev_ou_trop_court_est_refuse() {
        assertThat(VerificationProduction.verifier(VerificationProduction.SECRET_JWT_DE_DEV, PRIVEE, PUBLIQUE, true, CLE))
            .singleElement().asString().contains("JWT_SECRET");
        assertThat(VerificationProduction.verifier("x".repeat(31), PRIVEE, PUBLIQUE, true, CLE)).hasSize(1);
        assertThat(VerificationProduction.verifier(null, PRIVEE, PUBLIQUE, true, CLE)).hasSize(1);
    }

    @Test
    void les_cles_du_classpath_et_une_limitation_desactivee_sont_refusees() {
        assertThat(VerificationProduction.verifier(SECRET, "classpath:keys/ed25519_private_dev.pem",
            "classpath:keys/ed25519_public.pem", false, CLE)).hasSize(3);
    }

    @Test
    void toutes_les_erreurs_sont_signalees_ensemble_sans_reveler_les_valeurs() {
        String secretFaible = "secret-trop-court";
        assertThatThrownBy(() -> new VerificationProduction(secretFaible, "classpath:a", PUBLIQUE, true, CLE, CLE_PIECES,
                redisAvecPolitique("noeviction")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET")
            .hasMessageContaining("QR_PRIVATE_KEY_PATH")
            .hasMessageNotContaining(secretFaible);
    }

    @Test
    void une_cle_de_paiement_absente_est_refusee() {
        assertThat(VerificationProduction.verifier(SECRET, PRIVEE, PUBLIQUE, true, "")).singleElement()
            .asString().contains("UNITECHPAY_API_KEY");
        assertThat(VerificationProduction.verifier(SECRET, PRIVEE, PUBLIQUE, true, null)).hasSize(1);
    }

    @Test
    void une_politique_d_eviction_de_redis_autre_que_noeviction_empeche_le_demarrage() {
        assertThatThrownBy(() -> new VerificationProduction(SECRET, PRIVEE, PUBLIQUE, true, CLE, CLE_PIECES,
                redisAvecPolitique("allkeys-lru")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("allkeys-lru");
    }

    @Test
    void une_politique_illisible_laisse_demarrer_avec_un_avertissement() {
        StringRedisTemplate injoignable = mock(StringRedisTemplate.class);
        when(injoignable.execute(any(RedisCallback.class))).thenThrow(new QueryTimeoutException("CONFIG refusé"));

        assertThatCode(() -> new VerificationProduction(SECRET, PRIVEE, PUBLIQUE, true, CLE, CLE_PIECES, injoignable))
            .doesNotThrowAnyException();
        assertThatCode(() -> new VerificationProduction(SECRET, PRIVEE, PUBLIQUE, true, CLE, CLE_PIECES, redisAvecPolitique(null)))
            .doesNotThrowAnyException();
    }
}
