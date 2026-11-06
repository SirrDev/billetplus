package com.billetplus;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/orders et GET /api/orders/{id} (achat sans compte, ADR 0006), sur la
 * vraie pile : PostgreSQL pour la commande, Redis pour la réservation des places.
 *
 * Événements et catégories insérés en SQL (base partagée : identifiants et
 * numéros de téléphone aléatoires, pour que chaque test ne voie que ses données).
 */
class CommandesHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String JETON = "X-Jeton-Commande";

    @Autowired
    private JdbcTemplate jdbc;

    private static String telephone() {
        return "77" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000);
    }

    private UUID evenement(String statut) {
        UUID lieu = UUID.randomUUID();
        jdbc.update("INSERT INTO venues (id, nom, adresse, ville_code) VALUES (?, 'Arena', 'Dakar', 'SN-DAKAR')", lieu);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, titre, debute_le, venue_id, organizer_id, status) VALUES (?, ?, ?, ?, ?, ?)",
            id, "Concert " + id, Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS)), lieu, UUID.randomUUID(), statut);
        return id;
    }

    private UUID categorie(UUID id, UUID evenement, long prix, int quota) {
        jdbc.update("INSERT INTO ticket_categories (id, event_id, nom, prix_fcfa, quota_total) VALUES (?, ?, 'Standard', ?, ?)",
            id, evenement, prix, quota);
        return id;
    }

    private UUID categorie(UUID evenement, long prix, int quota) {
        return categorie(UUID.randomUUID(), evenement, prix, quota);
    }

    private ResultActions commander(UUID evenement, String telephone, String lignes) throws Exception {
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventId\":\"" + evenement + "\",\"telephone\":\"" + telephone + "\",\"lignes\":[" + lignes + "]}"));
    }

    private static String ligne(UUID categorie, int quantite) {
        return "{\"categoryId\":\"" + categorie + "\",\"quantite\":" + quantite + "}";
    }

    @Test
    void un_anonyme_cree_une_commande_et_la_relit_avec_son_jeton() throws Exception {
        UUID evenement = evenement("PUBLIE");
        UUID standard = categorie(evenement, 5000, 100);
        UUID vip = categorie(evenement, 15000, 10);

        String reponse = commander(evenement, "+221 " + telephone(), ligne(standard, 2) + "," + ligne(vip, 1))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.commande.status").value("EN_ATTENTE_PAIEMENT"))
            .andExpect(jsonPath("$.commande.montantTotalFcfa").value(25000))
            .andExpect(jsonPath("$.commande.lignes.length()").value(2))
            .andExpect(jsonPath("$.commande.telephone").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(reponse, "$.commande.id");
        String jeton = JsonPath.read(reponse, "$.jetonAcces");
        assertThat(jeton).hasSize(43);
        Instant creeLe = Instant.parse(JsonPath.read(reponse, "$.commande.creeLe"));
        Instant expireLe = Instant.parse(JsonPath.read(reponse, "$.commande.expireLe"));
        assertThat(expireLe).isEqualTo(creeLe.plus(4, ChronoUnit.MINUTES));

        mvc.perform(get("/api/orders/" + id).header(JETON, jeton))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.montantTotalFcfa").value(25000))
            // Le jeton n'est remis qu'une fois : la lecture ne le renvoie pas.
            .andExpect(jsonPath("$.jetonAcces").doesNotExist());

        // La base ne garde que l'empreinte.
        Integer avecLeJeton = jdbc.queryForObject("SELECT count(*) FROM orders WHERE empreinte_jeton = ?",
            Integer.class, jeton);
        assertThat(avecLeJeton).isZero();
    }

    @Test
    void sans_le_bon_jeton_une_commande_est_indiscernable_d_une_commande_inconnue() throws Exception {
        UUID evenement = evenement("PUBLIE");
        String reponse = commander(evenement, telephone(), ligne(categorie(evenement, 5000, 10), 1))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(reponse, "$.commande.id");

        mvc.perform(get("/api/orders/" + id))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Commande introuvable"));
        mvc.perform(get("/api/orders/" + id).header(JETON, "faux"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Commande introuvable"));
        mvc.perform(get("/api/orders/" + UUID.randomUUID()).header(JETON, (String) JsonPath.read(reponse, "$.jetonAcces")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Commande introuvable"));
    }

    /**
     * La seconde catégorie manque de places : toute la commande est refusée, elle
     * n'est pas enregistrée, et les places déjà réservées sur la première sont rendues.
     * Les identifiants fixent l'ordre de réservation (première avant seconde).
     */
    @Test
    void si_une_categorie_manque_de_places_rien_n_est_garde_de_la_commande() throws Exception {
        UUID evenement = evenement("PUBLIE");
        long suffixe = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        UUID premiere = categorie(new UUID(1, suffixe), evenement, 5000, 10);
        UUID seconde = categorie(new UUID(2, suffixe), evenement, 15000, 1);
        // Un premier acheteur prend la seule place de la seconde catégorie.
        commander(evenement, telephone(), ligne(seconde, 1)).andExpect(status().isCreated());

        String perdant = telephone();
        commander(evenement, perdant, ligne(premiere, 10) + "," + ligne(seconde, 1))
            .andExpect(status().isConflict());

        Integer enregistrees = jdbc.queryForObject("SELECT count(*) FROM orders WHERE telephone = ?",
            Integer.class, "+221" + perdant);
        assertThat(enregistrees).isZero();
        // Les 10 places de la première catégorie ont été rendues : un autre acheteur les obtient.
        commander(evenement, telephone(), ligne(premiere, 10)).andExpect(status().isCreated());
    }

    @Test
    void une_categorie_inconnue_d_un_autre_evenement_ou_d_un_brouillon_repond_404() throws Exception {
        UUID publie = evenement("PUBLIE");
        UUID autre = evenement("PUBLIE");
        UUID brouillon = evenement("BROUILLON");

        commander(publie, telephone(), ligne(UUID.randomUUID(), 1)).andExpect(status().isNotFound());
        commander(publie, telephone(), ligne(categorie(autre, 5000, 10), 1)).andExpect(status().isNotFound());
        commander(brouillon, telephone(), ligne(categorie(brouillon, 5000, 10), 1)).andExpect(status().isNotFound());
        commander(evenement("ANNULE"), telephone(), ligne(UUID.randomUUID(), 1)).andExpect(status().isNotFound());
    }

    @Test
    void les_entrees_invalides_repondent_400() throws Exception {
        UUID evenement = evenement("PUBLIE");
        UUID cat = categorie(evenement, 5000, 100);

        commander(evenement, "338234567", ligne(cat, 1)).andExpect(status().isBadRequest());
        commander(evenement, "7".repeat(31), ligne(cat, 1)).andExpect(status().isBadRequest());
        commander(evenement, telephone(), ligne(cat, 21)).andExpect(status().isBadRequest());
        commander(evenement, telephone(), ligne(cat, 0)).andExpect(status().isBadRequest());
        commander(evenement, telephone(), "").andExpect(status().isBadRequest());
        commander(evenement, telephone(), ligne(cat, 1) + "," + ligne(cat, 1)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telephone\":\"771234567\",\"lignes\":[" + ligne(cat, 1) + "]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void un_meme_numero_ne_peut_avoir_plus_de_trois_commandes_en_attente_par_evenement() throws Exception {
        UUID evenement = evenement("PUBLIE");
        UUID cat = categorie(evenement, 5000, 100);
        String numero = telephone();

        for (int i = 0; i < 3; i++) {
            commander(evenement, numero, ligne(cat, 1)).andExpect(status().isCreated());
        }
        // Une autre écriture du même numéro ne contourne pas la limite.
        commander(evenement, "00221" + numero, ligne(cat, 1))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Trop de commandes en attente")));
        // La limite est par événement.
        UUID autre = evenement("PUBLIE");
        commander(autre, numero, ligne(categorie(autre, 5000, 10), 1)).andExpect(status().isCreated());
    }
}
