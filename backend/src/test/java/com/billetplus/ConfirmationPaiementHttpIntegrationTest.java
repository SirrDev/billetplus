package com.billetplus;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.jayway.jsonpath.JsonPath;
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
import java.util.HexFormat;
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
 * Du paiement aux billets, sur la vraie pile : commande créée par l'API, paiement
 * ouvert (passerelle simulée), puis webhook SIGNÉ reçu par la vraie route, vérifié
 * par le vrai code de signature, et billets émis par le vrai module tickets.
 */
class ConfirmationPaiementHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String JETON = "X-Jeton-Commande";
    private static final String WEBHOOK = "/api/paiements/unitechpay/webhook";

    @Autowired
    private JdbcTemplate jdbc;

    private record Achat(UUID evenement, UUID categorie, String commande, String jeton, String reference) {
    }

    /** Événement publié, catégorie à 5000 F, commande de 2 places, paiement Wave ouvert. */
    private Achat acheter(int quota) throws Exception {
        UUID lieu = UUID.randomUUID();
        jdbc.update("INSERT INTO venues (id, nom, adresse) VALUES (?, 'Arena', 'Dakar')", lieu);
        UUID evenement = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, titre, debute_le, venue_id, organizer_id, status) VALUES (?, ?, ?, ?, ?, 'PUBLIE')",
            evenement, "Concert " + evenement, Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS)), lieu, UUID.randomUUID());
        UUID categorie = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_categories (id, event_id, nom, prix_fcfa, quota_total) VALUES (?, ?, 'Standard', 5000, ?)",
            categorie, evenement, quota);
        String telephone = "77" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000);
        String creee = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + evenement + "\",\"telephone\":\"" + telephone
                    + "\",\"lignes\":[{\"categoryId\":\"" + categorie + "\",\"quantite\":2}]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String commande = JsonPath.read(creee, "$.commande.id");
        String jeton = JsonPath.read(creee, "$.jetonAcces");

        String reference = "wave_" + UUID.randomUUID();
        when(passerellePaiement.ouvrir(any()))
            .thenReturn(new PasserellePaiement.PaiementCree(reference, 18, "https://pay.wave.com/c/" + reference));
        mvc.perform(post("/api/orders/" + commande + "/paiement").header(JETON, jeton)
                .contentType(MediaType.APPLICATION_JSON).content("{\"moyen\":\"WAVE\"}"))
            .andExpect(status().isOk());
        return new Achat(evenement, categorie, commande, jeton, reference);
    }

    private static String webhook(String cle, String event, String reference, long montant, String statut) {
        String signeLe = Long.toString(Instant.now().getEpochSecond());
        String signature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

    private ResultActions envoyer(String corps) throws Exception {
        return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private String statut(Achat achat) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?::uuid", String.class, achat.commande());
    }

    private int billets(Achat achat) {
        return jdbc.queryForObject("SELECT count(*) FROM tickets WHERE order_id = ?::uuid", Integer.class, achat.commande());
    }

    @Test
    void un_paiement_confirme_emet_les_billets_une_seule_fois() throws Exception {
        Achat achat = acheter(50);
        String corps = webhook(CLE_UNITECHPAY_DE_TEST, "payment_completed", achat.reference(), 10000, "completed");

        envoyer(corps).andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("BILLETS_EMIS"));
        assertThat(statut(achat)).isEqualTo("PAYEE");
        assertThat(billets(achat)).isEqualTo(2);
        Integer vendus = jdbc.queryForObject("SELECT quota_vendu FROM ticket_categories WHERE id = ?", Integer.class,
            achat.categorie());
        assertThat(vendus).isEqualTo(2);

        // Le même webhook rejoué : 200, rien de plus.
        envoyer(corps).andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("SANS_EFFET"));
        assertThat(billets(achat)).isEqualTo(2);
        // L'acheteur voit sa commande payée.
        mvc.perform(get("/api/orders/" + achat.commande()).header(JETON, achat.jeton()))
            .andExpect(jsonPath("$.status").value("PAYEE"));
    }

    @Test
    void une_signature_fausse_est_refusee_sans_rien_traiter() throws Exception {
        Achat achat = acheter(50);

        envoyer(webhook("cle-d-un-attaquant", "payment_completed", achat.reference(), 10000, "completed"))
            .andExpect(status().isUnauthorized());
        envoyer("pas du json").andExpect(status().isBadRequest());
        envoyer("x".repeat(20_000)).andExpect(status().isBadRequest());
        assertThat(statut(achat)).isEqualTo("EN_ATTENTE_PAIEMENT");
        assertThat(billets(achat)).isZero();
    }

    @Test
    void un_montant_incorrect_est_a_rembourser_et_visible_de_l_administrateur_seulement() throws Exception {
        Achat achat = acheter(50);

        envoyer(webhook(CLE_UNITECHPAY_DE_TEST, "payment_completed", achat.reference(), 100, "completed"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("A_REMBOURSER"));
        assertThat(statut(achat)).isEqualTo("PAYEE_SANS_BILLET");
        assertThat(billets(achat)).isZero();

        String liste = mvc.perform(get("/api/admin/commandes-a-rembourser").with(connecte(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<String>) JsonPath.read(liste, "$[?(@.id == '" + achat.commande() + "')].motif"))
            .containsExactly("MONTANT_INCORRECT");
        mvc.perform(get("/api/admin/commandes-a-rembourser").with(connecte(UUID.randomUUID(), "ORGANISATEUR")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/commandes-a-rembourser")).andExpect(status().isUnauthorized());
    }

    @Test
    void un_evenement_annule_avant_la_confirmation_donne_un_paiement_a_rembourser_sans_billet() throws Exception {
        Achat achat = acheter(50);
        jdbc.update("UPDATE events SET status = 'ANNULE' WHERE id = ?", achat.evenement());

        envoyer(webhook(CLE_UNITECHPAY_DE_TEST, "payment_completed", achat.reference(), 10000, "completed"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("A_REMBOURSER"));
        assertThat(statut(achat)).isEqualTo("PAYEE_SANS_BILLET");
        assertThat(jdbc.queryForObject("SELECT motif_sans_billet FROM orders WHERE id = ?::uuid", String.class,
            achat.commande())).isEqualTo("VENTE_FERMEE");
        assertThat(billets(achat)).isZero();
    }

    /** Quota épuisé par ailleurs : l'émission est annulée en entier, registre d'émission compris. */
    @Test
    void des_places_epuisees_donnent_un_paiement_a_rembourser_sans_aucune_trace_d_emission() throws Exception {
        Achat achat = acheter(50);
        jdbc.update("UPDATE ticket_categories SET quota_vendu = quota_total WHERE id = ?", achat.categorie());

        envoyer(webhook(CLE_UNITECHPAY_DE_TEST, "payment_completed", achat.reference(), 10000, "completed"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("A_REMBOURSER"));
        assertThat(jdbc.queryForObject("SELECT motif_sans_billet FROM orders WHERE id = ?::uuid", String.class,
            achat.commande())).isEqualTo("PLACES_INDISPONIBLES");
        assertThat(billets(achat)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_issuances WHERE order_id = ?::uuid", Integer.class,
            achat.commande())).isZero();
    }

    @Test
    void un_paiement_echoue_annule_la_commande_et_rend_ses_places() throws Exception {
        Achat achat = acheter(2);

        envoyer(webhook(CLE_UNITECHPAY_DE_TEST, "payment_failed", achat.reference(), 10000, "failed"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("ANNULEE"));
        assertThat(statut(achat)).isEqualTo("ANNULEE");

        // Les 2 seules places de la catégorie sont de nouveau réservables.
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + achat.evenement() + "\",\"telephone\":\"781234567\",\"lignes\":[{\"categoryId\":\""
                    + achat.categorie() + "\",\"quantite\":2}]}"))
            .andExpect(status().isCreated());
    }

    @Test
    void une_reference_inconnue_repond_200_sans_effet() throws Exception {
        envoyer(webhook(CLE_UNITECHPAY_DE_TEST, "payment_completed", "wave_inconnue_" + UUID.randomUUID(), 5000, "completed"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("COMMANDE_INCONNUE"));
    }
}
