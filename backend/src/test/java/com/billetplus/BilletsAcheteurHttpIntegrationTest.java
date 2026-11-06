package com.billetplus;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/orders/{id}/billets : l'acheteur récupère ses billets après le paiement,
 * sur la vraie pile (commande, paiement simulé, webhook signé, émission réelle).
 * Le signataire Ed25519 est simulé (@MockBean) : il renvoie « qr-<id du billet> ».
 */
class BilletsAcheteurHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String JETON = "X-Jeton-Commande";

    @Autowired
    private JdbcTemplate jdbc;

    private record Commande(String id, String jeton, String reference) {
    }

    @BeforeEach
    void signatureSimulee() {
        when(qrSigner.sign(any(), any(), any())).thenAnswer(i -> "qr-" + i.getArgument(0));
    }

    private Commande commandeDeDeuxPlaces() throws Exception {
        UUID lieu = UUID.randomUUID();
        jdbc.update("INSERT INTO venues (id, nom, adresse, ville_code) VALUES (?, 'Arena', 'Dakar', 'SN-DAKAR')", lieu);
        UUID evenement = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, titre, debute_le, venue_id, organizer_id, status) VALUES (?, ?, ?, ?, ?, 'PUBLIE')",
            evenement, "Concert " + evenement, Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS)), lieu, UUID.randomUUID());
        UUID categorie = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_categories (id, event_id, nom, prix_fcfa, quota_total) VALUES (?, ?, 'Tribune', 5000, 50)",
            categorie, evenement);
        String creee = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + evenement + "\",\"telephone\":\"77" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000)
                    + "\",\"lignes\":[{\"categoryId\":\"" + categorie + "\",\"quantite\":2}]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(creee, "$.commande.id");
        String jeton = JsonPath.read(creee, "$.jetonAcces");
        String reference = "wave_" + UUID.randomUUID();
        when(passerellePaiement.ouvrir(any()))
            .thenReturn(new PasserellePaiement.PaiementCree(reference, 18, "https://pay.wave.com/c/" + reference));
        mvc.perform(post("/api/orders/" + id + "/paiement").header(JETON, jeton)
                .contentType(MediaType.APPLICATION_JSON).content("{\"moyen\":\"WAVE\"}"))
            .andExpect(status().isOk());
        return new Commande(id, jeton, reference);
    }

    private void payer(Commande commande) throws Exception {
        String signeLe = Long.toString(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CLE_UNITECHPAY_DE_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(
            ("payment_completed|" + commande.reference() + "|10000|completed|" + signeLe).getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/api/paiements/unitechpay/webhook").contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment_completed\",\"reference\":\"" + commande.reference()
                    + "\",\"amount\":10000,\"status\":\"completed\",\"signed_at\":" + signeLe
                    + ",\"signature\":\"" + signature + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultat").value("BILLETS_EMIS"));
    }

    @Test
    void apres_le_paiement_l_acheteur_recupere_ses_deux_billets_avec_leur_qr() throws Exception {
        Commande commande = commandeDeDeuxPlaces();
        payer(commande);

        String reponse = mvc.perform(get("/api/orders/" + commande.id() + "/billets").header(JETON, commande.jeton()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].categorie").value("Tribune"))
            .andExpect(jsonPath("$[0].statut").value("EMIS"))
            .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(reponse, "$[*].id");
        List<String> qrs = JsonPath.read(reponse, "$[*].qr");
        // Chaque QR est celui de SON billet (signé avec son identifiant).
        assertThat(qrs).containsExactlyInAnyOrder("qr-" + ids.get(0), "qr-" + ids.get(1));
        // Recalculé à l'identique à chaque lecture.
        mvc.perform(get("/api/orders/" + commande.id() + "/billets").header(JETON, commande.jeton()))
            .andExpect(jsonPath("$[0].qr").value(qrs.get(0)));
    }

    @Test
    void un_billet_annule_n_a_plus_de_qr() throws Exception {
        Commande commande = commandeDeDeuxPlaces();
        payer(commande);
        jdbc.update("UPDATE tickets SET status = 'ANNULE' WHERE order_id = ?::uuid", commande.id());

        mvc.perform(get("/api/orders/" + commande.id() + "/billets").header(JETON, commande.jeton()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].statut").value("ANNULE"))
            .andExpect(jsonPath("$[0].qr").doesNotExist())
            .andExpect(jsonPath("$[1].qr").doesNotExist());
    }

    @Test
    void avant_le_paiement_409_et_sans_le_bon_jeton_404() throws Exception {
        Commande commande = commandeDeDeuxPlaces();

        mvc.perform(get("/api/orders/" + commande.id() + "/billets").header(JETON, commande.jeton()))
            .andExpect(status().isConflict());
        payer(commande);
        mvc.perform(get("/api/orders/" + commande.id() + "/billets").header(JETON, "faux"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + commande.id() + "/billets"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + UUID.randomUUID() + "/billets").header(JETON, commande.jeton()))
            .andExpect(status().isNotFound());
    }
}
