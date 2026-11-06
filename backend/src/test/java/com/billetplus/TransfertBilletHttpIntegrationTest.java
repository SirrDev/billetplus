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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transfert d'un billet (ADR 0008) sur la vraie pile : commande payée par webhook
 * signé, transfert par l'acheteur, consultation par le destinataire.
 * Le signataire est simulé : il renvoie « qr-<billet>-<date signée en ms> », ce qui
 * montre que le transfert re-signe le billet avec une nouvelle date.
 */
class TransfertBilletHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String JETON_COMMANDE = "X-Jeton-Commande";
    private static final String JETON_BILLET = "X-Jeton-Billet";
    private static final String DESTINATAIRE = "+221781234567";

    @Autowired
    private JdbcTemplate jdbc;

    private record Commande(String id, String jeton, String reference, UUID evenement) {
    }

    @BeforeEach
    void signatureSimulee() {
        when(qrSigner.sign(any(), any(), any())).thenAnswer(i ->
            "qr-" + i.getArgument(0) + "-" + ((Instant) i.getArgument(2)).toEpochMilli());
    }

    private Commande commandePayee() throws Exception {
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
        mvc.perform(post("/api/orders/" + id + "/paiement").header(JETON_COMMANDE, jeton)
                .contentType(MediaType.APPLICATION_JSON).content("{\"moyen\":\"WAVE\"}"))
            .andExpect(status().isOk());
        Commande commande = new Commande(id, jeton, reference, evenement);
        payer(commande);
        return commande;
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

    private String premierBillet(Commande commande) throws Exception {
        String billets = mvc.perform(get("/api/orders/" + commande.id() + "/billets").header(JETON_COMMANDE, commande.jeton()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(billets, "$[0].id");
    }

    private String transferer(Commande commande, String billet, String telephone) throws Exception {
        String reponse = mvc.perform(post("/api/orders/" + commande.id() + "/billets/" + billet + "/transfert")
                .header(JETON_COMMANDE, commande.jeton())
                .contentType(MediaType.APPLICATION_JSON).content("{\"telephone\":\"" + telephone + "\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.ticketId").value(billet))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.jeton");
    }

    private static String telephone(String e164) {
        return "{\"telephone\":\"" + e164 + "\"}";
    }

    @Test
    void le_destinataire_recoit_un_nouveau_qr_et_l_acheteur_perd_le_sien() throws Exception {
        Commande commande = commandePayee();
        String billet = premierBillet(commande);
        List<String> qrs = JsonPath.read(mvc.perform(get("/api/orders/" + commande.id() + "/billets")
                .header(JETON_COMMANDE, commande.jeton())).andReturn().getResponse().getContentAsString(),
            "$[?(@.id == '" + billet + "')].qr");
        String ancienQr = qrs.get(0);

        // Saisie libre du numéro : normalisée par orders.
        String jeton = transferer(commande, billet, "78 123 45 67");
        assertThat(jeton).hasSize(43);
        // Seule l'empreinte est stockée, jamais le jeton.
        assertThat(jdbc.queryForObject("SELECT transfert_jeton_empreinte FROM tickets WHERE id = ?::uuid",
            String.class, billet)).hasSize(64).isNotEqualTo(jeton);

        mvc.perform(get("/api/orders/" + commande.id() + "/billets").header(JETON_COMMANDE, commande.jeton()))
            .andExpect(jsonPath("$[?(@.id == '" + billet + "')].statut").value("TRANSFERE"))
            .andExpect(jsonPath("$[?(@.id == '" + billet + "')].transfereA").value("78 ••• •• 67"))
            .andExpect(jsonPath("$[?(@.id == '" + billet + "')].qr").value((Object) null));

        String recu = mvc.perform(post("/api/billets/" + billet + "/consultation").header(JETON_BILLET, jeton)
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.eventId").value(commande.evenement().toString()))
            .andExpect(jsonPath("$.categorie").value("Tribune"))
            .andExpect(jsonPath("$.statut").value("EMIS"))
            .andReturn().getResponse().getContentAsString();
        String nouveauQr = JsonPath.read(recu, "$.qr");
        // Même billet, date signée différente : l'ancien QR ne correspond plus à la base.
        assertThat(nouveauQr).startsWith("qr-" + billet + "-").isNotEqualTo(ancienQr);
    }

    @Test
    void le_lien_seul_ou_le_numero_seul_ne_suffisent_pas() throws Exception {
        Commande commande = commandePayee();
        String billet = premierBillet(commande);
        String jeton = transferer(commande, billet, DESTINATAIRE);

        // Bon jeton, autre numéro ; bon numéro, autre jeton ; sans jeton : 404 identiques.
        mvc.perform(post("/api/billets/" + billet + "/consultation").header(JETON_BILLET, jeton)
                .contentType(MediaType.APPLICATION_JSON).content(telephone("+221771111111")))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/billets/" + billet + "/consultation").header(JETON_BILLET, "faux" + jeton)
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/billets/" + billet + "/consultation")
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isNotFound());
        // Un billet jamais transféré ne s'ouvre pas non plus par cette route.
        List<String> autres = JsonPath.read(mvc.perform(get("/api/orders/" + commande.id() + "/billets")
                .header(JETON_COMMANDE, commande.jeton())).andReturn().getResponse().getContentAsString(),
            "$[?(@.id != '" + billet + "')].id");
        String autre = autres.get(0);
        mvc.perform(post("/api/billets/" + autre + "/consultation").header(JETON_BILLET, jeton)
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isNotFound());
        // Format du numéro : 400 de validation.
        mvc.perform(post("/api/billets/" + billet + "/consultation").header(JETON_BILLET, jeton)
                .contentType(MediaType.APPLICATION_JSON).content(telephone("781234567")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void le_transfert_est_definitif() throws Exception {
        Commande commande = commandePayee();
        String billet = premierBillet(commande);
        String jeton = transferer(commande, billet, DESTINATAIRE);

        mvc.perform(post("/api/orders/" + commande.id() + "/billets/" + billet + "/transfert")
                .header(JETON_COMMANDE, commande.jeton())
                .contentType(MediaType.APPLICATION_JSON).content(telephone("+221771234567")))
            .andExpect(status().isConflict());
        // Le premier destinataire garde son billet.
        mvc.perform(post("/api/billets/" + billet + "/consultation").header(JETON_BILLET, jeton)
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isOk());
    }

    @Test
    void seul_le_detenteur_de_la_commande_transfere_un_billet_valable_de_cette_commande() throws Exception {
        Commande commande = commandePayee();
        Commande autre = commandePayee();
        String billet = premierBillet(commande);
        String transfert = "/api/orders/" + commande.id() + "/billets/" + billet + "/transfert";

        mvc.perform(post(transfert).contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isNotFound());
        mvc.perform(post(transfert).header(JETON_COMMANDE, autre.jeton())
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isNotFound());
        // Billet d'une autre commande, avec le jeton de celle-ci : 404, comme un inconnu.
        mvc.perform(post("/api/orders/" + autre.id() + "/billets/" + billet + "/transfert").header(JETON_COMMANDE, autre.jeton())
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isNotFound());
        mvc.perform(post(transfert).header(JETON_COMMANDE, commande.jeton())
                .contentType(MediaType.APPLICATION_JSON).content(telephone("12345")))
            .andExpect(status().isBadRequest());
        // Billet déjà scanné : plus transférable.
        jdbc.update("UPDATE tickets SET status = 'SCANNE', scanne_le = now() WHERE id = ?::uuid", billet);
        mvc.perform(post(transfert).header(JETON_COMMANDE, commande.jeton())
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT transfere_a FROM tickets WHERE id = ?::uuid", String.class, billet)).isNull();
    }

    /** Deux transferts simultanés du même billet : un seul réussit (verrou @Version ou refus du domaine). */
    @Test
    void deux_transferts_simultanes_un_seul_reussit() throws Exception {
        Commande commande = commandePayee();
        String billet = premierBillet(commande);
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> codes = List.of("+221781111111", "+221782222222").stream()
                .map(numero -> pool.submit(() -> {
                    depart.await();
                    return mvc.perform(post("/api/orders/" + commande.id() + "/billets/" + billet + "/transfert")
                            .header(JETON_COMMANDE, commande.jeton())
                            .contentType(MediaType.APPLICATION_JSON).content(telephone(numero)))
                        .andReturn().getResponse().getStatus();
                }))
                .toList();
            depart.countDown();
            List<Integer> obtenus = new ArrayList<>();
            for (Future<Integer> code : codes) {
                obtenus.add(code.get(30, TimeUnit.SECONDS));
            }
            assertThat(obtenus).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE id = ?::uuid AND transfere_a IS NOT NULL",
            Integer.class, billet)).isEqualTo(1);
    }

    @Test
    void un_evenement_annule_ferme_le_transfert() throws Exception {
        Commande commande = commandePayee();
        String billet = premierBillet(commande);
        jdbc.update("UPDATE events SET status = 'ANNULE' WHERE id = ?", commande.evenement());

        mvc.perform(post("/api/orders/" + commande.id() + "/billets/" + billet + "/transfert")
                .header(JETON_COMMANDE, commande.jeton())
                .contentType(MediaType.APPLICATION_JSON).content(telephone(DESTINATAIRE)))
            .andExpect(status().isConflict());
    }
}
