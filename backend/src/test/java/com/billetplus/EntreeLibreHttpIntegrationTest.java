package com.billetplus;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entrée libre et crédit prépayé (ADR 0010) sur la vraie pile : recharge ouverte
 * (passerelle simulée) puis créditée par un webhook SIGNÉ, commande gratuite confirmée
 * tout de suite, débit atomique, refus sans trace, plafond par numéro, droits.
 */
class EntreeLibreHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String WEBHOOK = "/api/paiements/unitechpay/webhook";
    private static final String MESSAGE_CREDIT = "Les entrées gratuites de cet événement sont momentanément épuisées.";

    @Autowired
    private JdbcTemplate jdbc;

    private record Evenement(UUID id, UUID organisateur, UUID gratuite, UUID payante) {
    }

    @BeforeEach
    void signatureDesBillets() {
        when(qrSigner.sign(any(), any(), any())).thenAnswer(i -> "qr-" + i.getArgument(0));
    }

    /** Événement publié : une catégorie gratuite et une payante, de ces quotas. */
    private Evenement evenement(int quotaGratuit, int quotaPayant) {
        UUID lieu = UUID.randomUUID();
        jdbc.update("INSERT INTO venues (id, nom, adresse) VALUES (?, 'Sorano', 'Dakar')", lieu);
        UUID id = UUID.randomUUID();
        UUID orga = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, titre, debute_le, venue_id, organizer_id, status) VALUES (?, ?, ?, ?, ?, 'PUBLIE')",
            id, "Fete " + id, Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS)), lieu, orga);
        UUID gratuite = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_categories (id, event_id, nom, prix_fcfa, quota_total) VALUES (?, ?, 'Libre', 0, ?)",
            gratuite, id, quotaGratuit);
        UUID payante = UUID.randomUUID();
        if (quotaPayant > 0) {  // CHECK (quota_total > 0) : sans places, pas de catégorie payante
            jdbc.update("INSERT INTO ticket_categories (id, event_id, nom, prix_fcfa, quota_total) VALUES (?, ?, 'VIP', 5000, ?)",
                payante, id, quotaPayant);
        }
        return new Evenement(id, orga, gratuite, payante);
    }

    private static String telephone() {
        return "77" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000);
    }

    private static String commande(UUID evenement, String telephone, UUID categorie, int quantite) {
        return "{\"eventId\":\"" + evenement + "\",\"telephone\":\"" + telephone + "\",\"lignes\":[{\"categoryId\":\""
            + categorie + "\",\"quantite\":" + quantite + "}]}";
    }

    private ResultActions entreeLibre(String corps) throws Exception {
        return mvc.perform(post("/api/orders/entree-libre").contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private static String webhook(String event, String reference, long montant, String statut) {
        String signeLe = Long.toString(Instant.now().getEpochSecond());
        String signature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CLE_UNITECHPAY_DE_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            signature = HexFormat.of().formatHex(mac.doFinal(
                (event + "|" + reference + "|" + montant + "|" + statut + "|" + signeLe).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return """
            {"event":"%s","transaction_id":18,"reference":"%s","amount":%d,"status":"%s","method":"wave",
             "commission":75,"net_amount":%d,"timestamp":%s,"signed_at":%s,"signature":"%s"}"""
            .formatted(event, reference, montant, statut, montant - 75, signeLe, signeLe, signature);
    }

    /** Ouvre une recharge par l'API et renvoie la référence de paiement simulée. */
    private String ouvrirRecharge(UUID orga, long montant) throws Exception {
        String reference = "wave_recharge_" + UUID.randomUUID();
        when(passerellePaiement.ouvrir(any()))
            .thenReturn(new PasserellePaiement.PaiementCree(reference, 21, "https://pay.wave.com/c/" + reference));
        mvc.perform(post("/api/credit/recharges").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantFcfa\":" + montant + ",\"moyen\":\"WAVE\",\"telephone\":\"771234567\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://pay.wave.com/c/" + reference));
        return reference;
    }

    private void recharger(UUID orga, long montant) throws Exception {
        String reference = ouvrirRecharge(orga, montant);
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                .content(webhook("payment_completed", reference, montant, "completed")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultat").value("RECHARGE"));
    }

    private long solde(UUID orga) {
        List<Long> soldes = jdbc.queryForList("SELECT solde_fcfa FROM credits_organisateurs WHERE organisateur_id = ?",
            Long.class, orga);
        return soldes.isEmpty() ? 0 : soldes.get(0);
    }

    /** Le solde égale toujours la somme du journal (ADR 0010). */
    private void soldeEgaleLeJournal(UUID orga) {
        Long journal = jdbc.queryForObject(
            "SELECT coalesce(sum(montant_fcfa), 0) FROM mouvements_credit WHERE organisateur_id = ?", Long.class, orga);
        assertThat(solde(orga)).isEqualTo(journal);
    }

    private int billets(UUID evenement) {
        return jdbc.queryForObject("SELECT count(*) FROM tickets WHERE event_id = ?", Integer.class, evenement);
    }

    @Test
    void une_recharge_payee_credite_une_fois_puis_l_entree_libre_debite_75_f_par_billet() throws Exception {
        Evenement e = evenement(50, 30);  // 80 places : 75 F par billet
        String reference = ouvrirRecharge(e.organisateur(), 1_000);
        String paye = webhook("payment_completed", reference, 1_000, "completed");
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(paye)).andExpect(status().isOk());
        // Le même webhook rejoué : rien de plus.
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(paye)).andExpect(status().isOk());
        assertThat(solde(e.organisateur())).isEqualTo(1_000);

        String creee = entreeLibre(commande(e.id(), telephone(), e.gratuite(), 2))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.commande.status").value("PAYEE"))
            .andExpect(jsonPath("$.commande.montantTotalFcfa").value(0))
            .andExpect(jsonPath("$.jetonAcces").isString())
            .andReturn().getResponse().getContentAsString();
        String commande = JsonPath.read(creee, "$.commande.id");

        assertThat(solde(e.organisateur())).isEqualTo(850);
        assertThat(billets(e.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT tarif_entree_libre_fcfa FROM orders WHERE id = ?::uuid", Integer.class,
            commande)).isEqualTo(75);
        soldeEgaleLeJournal(e.organisateur());

        mvc.perform(get("/api/credit").with(connecte(e.organisateur(), "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.soldeFcfa").value(850))
            .andExpect(jsonPath("$.tarif.tarifPetitFcfa").value(75))
            .andExpect(jsonPath("$.mouvements[0].type").value("ENTREE_LIBRE"))
            .andExpect(jsonPath("$.mouvements[0].montantFcfa").value(-150))
            .andExpect(jsonPath("$.recharges[0].status").value("PAYEE"));
    }

    @Test
    void au_dela_de_100_places_gratuites_un_billet_coute_50_f() throws Exception {
        Evenement e = evenement(101, 10);  // 101 places gratuites
        recharger(e.organisateur(), 1_000);

        entreeLibre(commande(e.id(), telephone(), e.gratuite(), 3)).andExpect(status().isCreated());

        assertThat(solde(e.organisateur())).isEqualTo(850);
        soldeEgaleLeJournal(e.organisateur());
    }

    /** Décision de SirrDev : des places payantes jamais vendues ne font pas baisser le tarif. */
    @Test
    void les_places_payantes_ne_comptent_pas_dans_le_seuil() throws Exception {
        Evenement e = evenement(30, 100);  // 130 places, dont 30 gratuites
        recharger(e.organisateur(), 1_000);

        entreeLibre(commande(e.id(), telephone(), e.gratuite(), 2)).andExpect(status().isCreated());

        assertThat(solde(e.organisateur())).isEqualTo(850);  // 2 × 75 F
        soldeEgaleLeJournal(e.organisateur());
    }

    @Test
    void credit_insuffisant_409_sans_commande_ni_billet_ni_debit() throws Exception {
        Evenement e = evenement(50, 0);
        recharger(e.organisateur(), 1_000);
        jdbc.update("UPDATE credits_organisateurs SET solde_fcfa = 100 WHERE organisateur_id = ?", e.organisateur());
        jdbc.update("INSERT INTO mouvements_credit (id, organisateur_id, montant_fcfa, type, order_id, cree_le) "
            + "VALUES (?, ?, -900, 'ENTREE_LIBRE', ?, now())", UUID.randomUUID(), e.organisateur(), UUID.randomUUID());

        entreeLibre(commande(e.id(), telephone(), e.gratuite(), 2))  // 150 F > 100 F
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(MESSAGE_CREDIT));

        assertThat(solde(e.organisateur())).isEqualTo(100);
        assertThat(billets(e.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE event_id = ?", Integer.class, e.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT quota_vendu FROM ticket_categories WHERE id = ?", Integer.class,
            e.gratuite())).isZero();

        // Un seul billet (75 F) passe encore.
        entreeLibre(commande(e.id(), telephone(), e.gratuite(), 1)).andExpect(status().isCreated());
        assertThat(solde(e.organisateur())).isEqualTo(25);
        soldeEgaleLeJournal(e.organisateur());
    }

    @Test
    void deux_entrees_simultanees_ne_depensent_pas_le_meme_franc() throws Exception {
        Evenement e = evenement(50, 0);
        recharger(e.organisateur(), 1_000);
        jdbc.update("UPDATE credits_organisateurs SET solde_fcfa = 75 WHERE organisateur_id = ?", e.organisateur());
        jdbc.update("INSERT INTO mouvements_credit (id, organisateur_id, montant_fcfa, type, order_id, cree_le) "
            + "VALUES (?, ?, -925, 'ENTREE_LIBRE', ?, now())", UUID.randomUUID(), e.organisateur(), UUID.randomUUID());

        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> statuts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String corps = commande(e.id(), telephone(), e.gratuite(), 1);
            Callable<Integer> tache = () -> {
                depart.await();
                return entreeLibre(corps).andReturn().getResponse().getStatus();
            };
            statuts.add(pool.submit(tache));
        }
        depart.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : statuts) {
            codes.add(f.get());
        }
        pool.shutdown();

        assertThat(codes).containsExactlyInAnyOrder(201, 409);
        assertThat(solde(e.organisateur())).isZero();
        assertThat(billets(e.id())).isEqualTo(1);
        soldeEgaleLeJournal(e.organisateur());
    }

    @Test
    void plafond_de_billets_gratuits_par_numero() throws Exception {
        Evenement e = evenement(50, 0);
        recharger(e.organisateur(), 5_000);
        String numero = telephone();

        entreeLibre(commande(e.id(), numero, e.gratuite(), 8)).andExpect(status().isCreated());
        entreeLibre(commande(e.id(), numero, e.gratuite(), 3))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Un numéro peut obtenir au plus 10 billets gratuits pour cet événement"));
        entreeLibre(commande(e.id(), numero, e.gratuite(), 2)).andExpect(status().isCreated());

        assertThat(billets(e.id())).isEqualTo(10);
        assertThat(solde(e.organisateur())).isEqualTo(5_000 - 10 * 75);
    }

    @Test
    void deux_demandes_simultanees_du_meme_numero_ne_depassent_pas_le_plafond() throws Exception {
        Evenement e = evenement(50, 0);
        recharger(e.organisateur(), 5_000);
        String corps = commande(e.id(), telephone(), e.gratuite(), 6);  // 6 + 6 > 10

        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> statuts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<Integer> tache = () -> {
                depart.await();
                return entreeLibre(corps).andReturn().getResponse().getStatus();
            };
            statuts.add(pool.submit(tache));
        }
        depart.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : statuts) {
            codes.add(f.get());
        }
        pool.shutdown();

        assertThat(codes).containsExactlyInAnyOrder(201, 409);
        assertThat(billets(e.id())).isEqualTo(6);
        assertThat(solde(e.organisateur())).isEqualTo(5_000 - 6 * 75);
    }

    @Test
    void gratuit_et_payant_ne_se_melangent_pas_et_gardent_chacun_leur_route() throws Exception {
        Evenement e = evenement(50, 30);
        recharger(e.organisateur(), 1_000);
        String mixte = "{\"eventId\":\"" + e.id() + "\",\"telephone\":\"" + telephone() + "\",\"lignes\":["
            + "{\"categoryId\":\"" + e.gratuite() + "\",\"quantite\":1},"
            + "{\"categoryId\":\"" + e.payante() + "\",\"quantite\":1}]}";

        entreeLibre(mixte).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(mixte))
            .andExpect(status().isBadRequest());
        // Gratuit par la route payante, payant par l'entrée libre : refusés.
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(commande(e.id(), telephone(), e.gratuite(), 1)))
            .andExpect(status().isBadRequest());
        entreeLibre(commande(e.id(), telephone(), e.payante(), 1)).andExpect(status().isBadRequest());

        assertThat(solde(e.organisateur())).isEqualTo(1_000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE event_id = ?", Integer.class, e.id())).isZero();
    }

    @Test
    void une_recharge_au_mauvais_montant_ou_echouee_ne_credite_rien() throws Exception {
        UUID orga = UUID.randomUUID();
        String mauvaisMontant = ouvrirRecharge(orga, 2_000);
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                .content(webhook("payment_completed", mauvaisMontant, 200, "completed")))
            .andExpect(status().isOk());
        String echouee = ouvrirRecharge(orga, 3_000);
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                .content(webhook("payment_failed", echouee, 3_000, "failed")))
            .andExpect(status().isOk());

        assertThat(solde(orga)).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM recharges_credit WHERE organisateur_id = ? ORDER BY montant_fcfa",
            String.class, orga)).containsExactly("ANOMALIE", "ECHOUEE");
    }

    @Test
    void le_credit_est_reserve_a_son_organisateur() throws Exception {
        mvc.perform(get("/api/credit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/credit").with(connecte(UUID.randomUUID(), "ADMIN"))).andExpect(status().isForbidden());
        // Corps valide : @PreAuthorize s'évalue après @Valid (piège du CLAUDE.md).
        mvc.perform(post("/api/credit/recharges").with(connecte(UUID.randomUUID(), "AGENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantFcfa\":5000,\"moyen\":\"WAVE\",\"telephone\":\"771234567\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/credit/recharges").with(connecte(UUID.randomUUID(), "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantFcfa\":500,\"moyen\":\"WAVE\",\"telephone\":\"771234567\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/credit").with(connecte(UUID.randomUUID(), "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.soldeFcfa").value(0));
    }
}
