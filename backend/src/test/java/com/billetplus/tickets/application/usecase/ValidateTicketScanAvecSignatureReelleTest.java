package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.application.usecase.ResultatScan.Issue;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.infrastructure.crypto.Ed25519QRSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Contrôle d'entrée avec le VRAI signataire Ed25519 (paire de clés générée pour
 * le test), sans Spring : chemin complet entre la signature à l'émission
 * (IssueTicketUseCase signe ticket.getEmisLe(), à la microseconde) et la
 * vérification au scan (le QR n'en garde que la milliseconde).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidateTicketScanAvecSignatureReelleTest {

    @Mock
    private TicketRepository ticketRepository;

    private Ed25519QRSigner signer;
    private Ed25519QRSigner faussaire;
    private Ticket ticket;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generateur = KeyPairGenerator.getInstance("Ed25519");
        KeyPair cles = generateur.generateKeyPair();
        signer = new Ed25519QRSigner(cles.getPrivate(), cles.getPublic());
        // Un faussaire signe avec sa propre clé ; le scanner vérifie avec la clé publique du backend.
        KeyPair autresCles = generateur.generateKeyPair();
        faussaire = new Ed25519QRSigner(autresCles.getPrivate(), cles.getPublic());

        ticket = new Ticket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    }

    private ValidateTicketScanUseCase useCase() {
        return new ValidateTicketScanUseCase(signer, ticketRepository);
    }

    /** Signé comme à l'émission : avec emisLe tel qu'en base (microseconde). */
    private String qrEmis() {
        return signer.sign(ticket.getId(), ticket.getEventId(), ticket.getEmisLe());
    }

    @Test
    void accepte_un_qr_reellement_signe_a_l_emission() {
        assertThat(useCase().execute(qrEmis(), ticket.getEventId()).issue()).isEqualTo(Issue.ACCEPTE);
    }

    @Test
    void refuse_un_qr_dont_un_seul_caractere_de_la_signature_a_ete_modifie() {
        String qr = qrEmis();
        char dernier = qr.charAt(qr.length() - 1);
        String altere = qr.substring(0, qr.length() - 1) + (dernier == '0' ? '1' : '0');

        assertThat(useCase().execute(altere, ticket.getEventId())).isEqualTo(ResultatScan.invalide());
    }

    /** Recopier la signature d'un billet sur un autre identifiant ne marche pas. */
    @Test
    void refuse_un_qr_dont_le_contenu_signe_a_ete_modifie() {
        String qr = qrEmis();
        String autreIdentifiant = qr.replace(ticket.getId().toString(), UUID.randomUUID().toString());

        assertThat(useCase().execute(autreIdentifiant, ticket.getEventId())).isEqualTo(ResultatScan.invalide());
    }

    @Test
    void refuse_un_qr_signe_avec_une_autre_cle() {
        String forge = faussaire.sign(ticket.getId(), ticket.getEventId(), ticket.getEmisLe());

        assertThat(useCase().execute(forge, ticket.getEventId())).isEqualTo(ResultatScan.invalide());
    }

    @Test
    void refuse_un_contenu_qui_n_est_pas_un_qr_de_billet() {
        for (String contenu : new String[]{"bonjour", ".", "{}.00", "https://exemple.sn/billet"}) {
            assertThat(useCase().execute(contenu, ticket.getEventId())).isEqualTo(ResultatScan.invalide());
        }
    }

    /** Le même QR présenté deux fois : la seconde fois, il est signalé déjà scanné. */
    @Test
    void signale_le_second_passage_du_meme_qr() {
        String qr = qrEmis();

        assertThat(useCase().execute(qr, ticket.getEventId()).issue()).isEqualTo(Issue.ACCEPTE);
        ResultatScan second = useCase().execute(qr, ticket.getEventId());

        assertThat(second.issue()).isEqualTo(Issue.DEJA_SCANNE);
        assertThat(second.premierScanLe()).isEqualTo(ticket.getScanneLe());
    }
}
