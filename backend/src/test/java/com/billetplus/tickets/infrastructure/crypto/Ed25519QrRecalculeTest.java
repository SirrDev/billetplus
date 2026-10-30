package com.billetplus.tickets.infrastructure.crypto;

import com.billetplus.tickets.application.port.QrVerificationResult;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les QR ne sont jamais stockés : ils sont recalculés à chaque lecture des billets
 * (BilletsDeCommandeService). Cela n'est correct que si la signature Ed25519 est
 * déterministe (RFC 8032) : le même billet doit toujours donner le même QR.
 */
class Ed25519QrRecalculeTest {

    @Test
    void le_meme_billet_donne_toujours_le_meme_qr_et_il_reste_valable() throws Exception {
        KeyPair paire = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Ed25519QRSigner signataire = new Ed25519QRSigner(paire.getPrivate(), paire.getPublic());
        UUID billet = UUID.randomUUID();
        UUID evenement = UUID.randomUUID();
        // Précision du billet en base (microseconde) ; le QR signe la milliseconde.
        Instant emisLe = Instant.parse("2026-11-01T10:00:00.123456Z").truncatedTo(ChronoUnit.MICROS);

        String aLEmission = signataire.sign(billet, evenement, emisLe);
        String recalcule = signataire.sign(billet, evenement, emisLe);

        assertThat(recalcule).isEqualTo(aLEmission);
        QrVerificationResult verification = signataire.verify(recalcule);
        assertThat(verification.valide()).isTrue();
        assertThat(verification.ticketId()).isEqualTo(billet);
        assertThat(verification.eventId()).isEqualTo(evenement);
    }
}
