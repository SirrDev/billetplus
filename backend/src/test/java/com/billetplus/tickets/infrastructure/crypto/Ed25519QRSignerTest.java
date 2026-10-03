package com.billetplus.tickets.infrastructure.crypto;

import com.billetplus.tickets.application.port.QrVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paire de clés générée en mémoire pour chaque test — aucune dépendance à un
 * fichier PEM sur disque, aucun contexte Spring. C'est précisément le
 * bénéfice recherché en gardant Ed25519QRSigner indépendant du framework
 * (voir ADR 0002, niveau 3).
 */
class Ed25519QRSignerTest {

    private Ed25519QRSigner signer;
    private PublicKey autrePublicKey;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = generator.generateKeyPair();
        signer = new Ed25519QRSigner(keyPair.getPrivate(), keyPair.getPublic());

        KeyPair autrePaire = generator.generateKeyPair();
        autrePublicKey = autrePaire.getPublic();
    }

    @Test
    void verifie_avec_succes_un_qr_signe_par_la_bonne_cle() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        String qrContent = signer.sign(ticketId, eventId, issuedAt);
        QrVerificationResult result = signer.verify(qrContent);

        assertThat(result.valide()).isTrue();
        assertThat(result.ticketId()).isEqualTo(ticketId);
        assertThat(result.eventId()).isEqualTo(eventId);
        // La précision milliseconde est conservée par epoch millis — pas de perte.
        assertThat(result.issuedAt().toEpochMilli()).isEqualTo(issuedAt.toEpochMilli());
    }

    @Test
    void rejette_un_qr_signe_par_une_cle_different() throws NoSuchAlgorithmException {
        String qrContent = signer.sign(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        PrivateKey clePriveeInconnue = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate();
        Ed25519QRSigner signataireMalveillant = new Ed25519QRSigner(clePriveeInconnue, autrePublicKey);
        // Un attaquant qui ne connaît pas la vraie clé privée ne peut pas
        // produire un QR que le vérificateur légitime acceptera.
        String qrForge = signataireMalveillant.sign(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(signer.verify(qrForge).valide()).isFalse();
    }

    @Test
    void rejette_un_contenu_qr_corrompu() {
        assertThat(signer.verify("ceci-nest-pas-un-qr-valide").valide()).isFalse();
    }

    @Test
    void rejette_un_contenu_qr_dont_le_json_a_ete_altere_apres_signature() {
        String qrContent = signer.sign(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        String[] parts = qrContent.split("\\.", 2);

        // Falsification : on change un caractère du payload JSON sans
        // re-signer — la signature ne correspond plus au contenu modifié.
        String payloadAltere = parts[0].replace("tid", "tix");
        String qrFalsifie = payloadAltere + "." + parts[1];

        assertThat(signer.verify(qrFalsifie).valide()).isFalse();
    }

    @Test
    void rejette_un_qr_sans_separateur() {
        assertThat(signer.verify("pasDeSeparateurIci").valide()).isFalse();
    }
}
