package com.billetplus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limitation de débit sur la vraie pile (intercepteur, Redis, gestionnaire
 * d'exceptions), avec les règles d'application.yml.
 *
 * Chaque test fixe sa propre adresse IP (le contexte partagé en donne une
 * aléatoire par défaut, voir ContexteCompletTestBase) : une IP par test, tirée
 * dans un bloc réservé à la documentation (RFC 5737), pour ne jamais partager un
 * compteur avec un autre test.
 */
class LimitationDebitHttpIntegrationTest extends ContexteCompletTestBase {

    /**
     * Les fenêtres d'une minute sont alignées sur l'horloge : une boucle qui
     * chevaucherait la fin d'une minute verrait son compteur repartir de zéro, et
     * le 429 attendu n'arriverait pas. S'il reste moins de 15 s dans la minute
     * en cours, on attend la suivante (15 s d'attente au plus).
     */
    private static void attendreUneFenetreEntiere() throws InterruptedException {
        long restantMs = 60_000 - System.currentTimeMillis() % 60_000;
        if (restantMs < 15_000) {
            Thread.sleep(restantMs + 100);
        }
    }

    private static String ipUnique() {
        ThreadLocalRandom aleatoire = ThreadLocalRandom.current();
        return "198.51." + aleatoire.nextInt(256) + "." + aleatoire.nextInt(256);
    }

    private static MockHttpServletRequestBuilder depuis(MockHttpServletRequestBuilder requete, String ip) {
        return requete.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    private ResultActions commandeInvalide(String ip) throws Exception {
        // Corps invalide : la limitation passe AVANT la validation, la requête compte quand même.
        return mvc.perform(depuis(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"), ip));
    }

    @Test
    void au_dela_de_20_creations_de_commande_par_minute_une_ip_recoit_429() throws Exception {
        String ip = ipUnique();
        attendreUneFenetreEntiere();
        for (int i = 0; i < 20; i++) {
            commandeInvalide(ip).andExpect(status().isBadRequest());
        }

        commandeInvalide(ip)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.message").value(startsWith("Trop de requêtes")));

        // Une autre adresse n'est pas touchée.
        commandeInvalide(ipUnique()).andExpect(status().isBadRequest());
    }

    /**
     * Lectures publiques (catalogue, détail, catégories, lieu, villes) : une IP qui dépasse la
     * limite reçoit 429 ; une autre IP n'est pas touchée. Identifiants inconnus : 404 rapide,
     * mais la requête compte quand même (la limitation passe avant le contrôleur).
     */
    @Test
    void les_lectures_publiques_sont_limitees_par_ip() throws Exception {
        UUID inconnu = UUID.randomUUID();
        Object[][] cas = {
            {"/api/events", 300, 200},
            {"/api/events/" + inconnu, 300, 404},
            {"/api/events/" + inconnu + "/categories", 300, 404},
            {"/api/venues/" + inconnu, 300, 404},
            {"/api/villes", 60, 200},
        };
        for (Object[] c : cas) {
            // Chaque cas dans une seule fenêtre d'une minute (voir attendreUneFenetreEntiere).
            attendreUneFenetreEntiere();
            String chemin = (String) c[0];
            int limite = (int) c[1];
            int attendu = (int) c[2];
            String ip = ipUnique();
            for (int i = 0; i < limite; i++) {
                mvc.perform(depuis(get(chemin), ip)).andExpect(status().is(attendu));
            }
            mvc.perform(depuis(get(chemin), ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
            mvc.perform(depuis(get(chemin), ipUnique())).andExpect(status().is(attendu));
        }
    }

    @Test
    void au_dela_de_10_connexions_par_minute_une_ip_recoit_429_meme_avec_de_mauvais_identifiants() throws Exception {
        String ip = ipUnique();
        String corps = "{\"email\":\"inconnu-" + UUID.randomUUID() + "@billetplus.sn\",\"motDePasse\":\"mauvaismdp\"}";
        attendreUneFenetreEntiere();
        for (int i = 0; i < 10; i++) {
            mvc.perform(depuis(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corps), ip))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(depuis(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corps), ip))
            .andExpect(status().isTooManyRequests());
    }

    /** Clé COMPTE : un agent qui change d'adresse IP reste limité. */
    @Test
    void au_dela_de_120_scans_par_minute_un_compte_agent_recoit_429_quelle_que_soit_son_ip() throws Exception {
        UUID agent = UUID.randomUUID();
        String route = "/api/events/" + UUID.randomUUID() + "/scans";
        attendreUneFenetreEntiere();
        for (int i = 0; i < 120; i++) {
            // Corps vide : 400, mais la requête est comptée. Chaque requête a une IP aléatoire.
            mvc.perform(post(route).with(connecte(agent, "AGENT")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post(route).with(connecte(agent, "AGENT")).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isTooManyRequests());

        // Un autre agent scanne normalement.
        mvc.perform(post(route).with(connecte(UUID.randomUUID(), "AGENT")).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void les_routes_sans_regle_ne_sont_pas_limitees() throws Exception {
        String ip = ipUnique();
        for (int i = 0; i < 150; i++) {
            mvc.perform(depuis(get("/api/events"), ip)).andExpect(status().isOk());
        }
    }
}
