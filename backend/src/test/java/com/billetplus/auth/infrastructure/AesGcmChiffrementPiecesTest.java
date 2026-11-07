package com.billetplus.auth.infrastructure;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmChiffrementPiecesTest {

    private static final byte[] CLAIR = "carte d'identite de test 0123456789 abcdefghij".getBytes(StandardCharsets.UTF_8);

    private static String cle(int octet) {
        byte[] b = new byte[32];
        Arrays.fill(b, (byte) octet);
        return Base64.getEncoder().encodeToString(b);
    }

    @Test
    void dechiffre_ce_qu_il_a_chiffre() {
        AesGcmChiffrementPieces c = new AesGcmChiffrementPieces(cle(7));

        assertThat(c.dechiffrer(c.chiffrer(CLAIR))).isEqualTo(CLAIR);
    }

    @Test
    void deux_chiffrements_du_meme_contenu_different() {
        AesGcmChiffrementPieces c = new AesGcmChiffrementPieces(cle(7));

        assertThat(c.chiffrer(CLAIR)).isNotEqualTo(c.chiffrer(CLAIR));
    }

    @Test
    void le_chiffre_ne_contient_pas_le_clair() {
        AesGcmChiffrementPieces c = new AesGcmChiffrementPieces(cle(7));

        byte[] chiffre = c.chiffrer(CLAIR);

        assertThat(new String(chiffre, StandardCharsets.ISO_8859_1)).doesNotContain("identite");
        assertThat(chiffre).hasSize(12 + CLAIR.length + 16);
    }

    @Test
    void un_contenu_altere_est_refuse() {
        AesGcmChiffrementPieces c = new AesGcmChiffrementPieces(cle(7));
        byte[] chiffre = c.chiffrer(CLAIR);
        chiffre[chiffre.length / 2] ^= 0x01;

        assertThatThrownBy(() -> c.dechiffrer(chiffre)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void un_nonce_altere_est_refuse() {
        AesGcmChiffrementPieces c = new AesGcmChiffrementPieces(cle(7));
        byte[] chiffre = c.chiffrer(CLAIR);
        chiffre[0] ^= 0x01;

        assertThatThrownBy(() -> c.dechiffrer(chiffre)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void une_autre_cle_est_refusee() {
        byte[] chiffre = new AesGcmChiffrementPieces(cle(7)).chiffrer(CLAIR);
        AesGcmChiffrementPieces autre = new AesGcmChiffrementPieces(cle(8));

        assertThatThrownBy(() -> autre.dechiffrer(chiffre)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void un_contenu_trop_court_est_refuse() {
        AesGcmChiffrementPieces c = new AesGcmChiffrementPieces(cle(7));

        assertThatThrownBy(() -> c.dechiffrer(new byte[12])).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.dechiffrer(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void une_cle_non_base64_est_refusee_sans_recopier_la_valeur() {
        String saisie = "SECRET-pas-du-base64-!!!";

        assertThatThrownBy(() -> new AesGcmChiffrementPieces(saisie))
            .isInstanceOf(IllegalStateException.class)
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain(saisie));
    }

    @Test
    void une_cle_de_16_octets_est_refusee_sans_recopier_la_valeur() {
        String saisie = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmChiffrementPieces(saisie))
            .isInstanceOf(IllegalStateException.class)
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain(saisie));
    }

    @Test
    void une_cle_de_33_octets_est_refusee() {
        String saisie = Base64.getEncoder().encodeToString(new byte[33]);

        assertThatThrownBy(() -> new AesGcmChiffrementPieces(saisie)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void une_cle_absente_est_refusee() {
        assertThatThrownBy(() -> new AesGcmChiffrementPieces(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void la_cle_de_developpement_est_valide() {
        AesGcmChiffrementPieces c = new AesGcmChiffrementPieces(AesGcmChiffrementPieces.CLE_DE_DEV);

        assertThat(c.dechiffrer(c.chiffrer(CLAIR))).isEqualTo(CLAIR);
    }
}
