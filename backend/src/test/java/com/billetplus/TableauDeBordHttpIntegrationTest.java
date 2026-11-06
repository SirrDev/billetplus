package com.billetplus;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tableau de bord de l'organisateur et remboursements d'un événement annulé, sur
 * la vraie pile : ventes réelles (webhook signé), réservations réelles (Redis).
 */
class TableauDeBordHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String JETON = "X-Jeton-Commande";

    @Autowired
    private JdbcTemplate jdbc;

    private UUID organisateur;
    private UUID evenement;
    private UUID categorie;

    @BeforeEach
    void evenementPublie() {
        when(qrSigner.sign(any(), any(), any())).thenAnswer(i -> "qr-" + i.getArgument(0));
        organisateur = UUID.randomUUID();
        UUID lieu = UUID.randomUUID();
        jdbc.update("INSERT INTO venues (id, nom, adresse, ville_code) VALUES (?, 'Arena', 'Dakar', 'SN-DAKAR')", lieu);
        evenement = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, titre, debute_le, venue_id, organizer_id, status) VALUES (?, ?, ?, ?, ?, 'PUBLIE')",
            evenement, "Concert " + evenement, Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS)), lieu, organisateur);
        categorie = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_categories (id, event_id, nom, prix_fcfa, quota_total) VALUES (?, ?, 'Tribune', 5000, 10)",
            categorie, evenement);
    }

    /** Commande de {@code quantite} places ; renvoie {id, jeton, reference} (paiement ouvert). */
    private String[] commander(int quantite) throws Exception {
        String creee = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + evenement + "\",\"telephone\":\"77" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000)
                    + "\",\"lignes\":[{\"categoryId\":\"" + categorie + "\",\"quantite\":" + quantite + "}]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(creee, "$.commande.id");
        String jeton = JsonPath.read(creee, "$.jetonAcces");
        String reference = "wave_" + UUID.randomUUID();
        when(passerellePaiement.ouvrir(any()))
            .thenReturn(new PasserellePaiement.PaiementCree(reference, 18, "https://pay.wave.com/c/" + reference));
        mvc.perform(post("/api/orders/" + id + "/paiement").header(JETON, jeton)
                .contentType(MediaType.APPLICATION_JSON).content("{\"moyen\":\"WAVE\"}"))
            .andExpect(status().isOk());
        return new String[] {id, jeton, reference};
    }

    private void payer(String reference, long montant) throws Exception {
        String signeLe = Long.toString(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CLE_UNITECHPAY_DE_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(
            ("payment_completed|" + reference + "|" + montant + "|completed|" + signeLe).getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/api/paiements/unitechpay/webhook").contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment_completed\",\"reference\":\"" + reference + "\",\"amount\":" + montant
                    + ",\"status\":\"completed\",\"signed_at\":" + signeLe + ",\"signature\":\"" + signature + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("BILLETS_EMIS"));
    }

    @Test
    void l_organisateur_voit_ventes_paiements_en_cours_scans_et_chiffre_d_affaires() throws Exception {
        String[] payee = commander(2);
        payer(payee[2], 10000);
        commander(3); // en cours de paiement : réservé dans Redis
        jdbc.update("UPDATE tickets SET status = 'SCANNE', scanne_le = now() WHERE id = "
            + "(SELECT id FROM tickets WHERE order_id = ?::uuid LIMIT 1)", payee[0]);

        mvc.perform(get("/api/events/" + evenement + "/tableau-de-bord").with(connecte(organisateur, "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].nom").value("Tribune"))
            .andExpect(jsonPath("$.categories[0].quotaTotal").value(10))
            .andExpect(jsonPath("$.categories[0].vendus").value(2))
            .andExpect(jsonPath("$.categories[0].enCoursDePaiement").value(3))
            .andExpect(jsonPath("$.categories[0].disponibles").value(5))
            .andExpect(jsonPath("$.categories[0].scannes").value(1))
            .andExpect(jsonPath("$.chiffreAffairesFcfa").value(10000));
    }

    @Test
    void seul_l_organisateur_de_l_evenement_y_a_acces() throws Exception {
        String route = "/api/events/" + evenement + "/tableau-de-bord";
        mvc.perform(get(route).with(connecte(UUID.randomUUID(), "ORGANISATEUR"))).andExpect(status().isNotFound());
        mvc.perform(get(route).with(connecte(UUID.randomUUID(), "AGENT"))).andExpect(status().isForbidden());
        mvc.perform(get(route)).andExpect(status().isUnauthorized());
    }

    @Test
    void les_commandes_payees_d_un_evenement_annule_sont_a_rembourser() throws Exception {
        String[] payee = commander(2);
        payer(payee[2], 10000);
        // Annulation par la vraie route : les billets sont annulés avec l'événement (ADR 0005).
        mvc.perform(post("/api/events/" + evenement + "/annuler").with(connecte(organisateur, "ORGANISATEUR")))
            .andExpect(status().isOk());

        String liste = mvc.perform(get("/api/admin/commandes-a-rembourser").with(connecte(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> motifs = JsonPath.read(liste, "$[?(@.id == '" + payee[0] + "')].motif");
        assertThat(motifs).containsExactly("EVENEMENT_ANNULE");
        List<Integer> montants = JsonPath.read(liste, "$[?(@.id == '" + payee[0] + "')].montantFcfa");
        assertThat(montants).containsExactly(10000);

        // Le tableau de bord ne compte plus ces billets dans le chiffre d'affaires.
        mvc.perform(get("/api/events/" + evenement + "/tableau-de-bord").with(connecte(organisateur, "ORGANISATEUR")))
            .andExpect(jsonPath("$.categories[0].annules").value(2))
            .andExpect(jsonPath("$.chiffreAffairesFcfa").value(0));
    }
}
