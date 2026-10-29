package com.billetplus;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests HTTP des routes antérieures à MockMvc (/api/auth, /api/events,
 * /api/venues), jusque-là vérifiées uniquement par les scénarios manuels de la
 * section 6 du HANDOFF.
 *
 * Contrairement aux autres tests du contexte complet, l'authentification passe
 * ici par de VRAIS jetons JWT (POST /api/auth/login, puis en-tête
 * Authorization: Bearer) : c'est JwtAuthenticationFilter lui-même qui est testé.
 */
class RoutesExistantesHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String MOT_DE_PASSE = "motdepasse";

    @Autowired
    private JdbcTemplate jdbc;

    private static String emailUnique() {
        return "compte-" + UUID.randomUUID() + "@billetplus.sn";
    }

    private void inscrire(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\",\"nomComplet\":\"Orga\"}"))
            .andExpect(status().isCreated());
    }

    /** Inscrit un organisateur et renvoie l'en-tête « Bearer <jeton d'accès> ». */
    private String organisateurConnecte() throws Exception {
        String email = emailUnique();
        inscrire(email);
        String reponse = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.accessToken");
    }

    private String creerLieu(String jeton) throws Exception {
        String reponse = mvc.perform(post("/api/venues").header(HttpHeaders.AUTHORIZATION, jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Grand Theatre\",\"adresse\":\"Dakar\",\"latitude\":14.7,\"longitude\":-17.4}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private String creerEvenement(String jeton) throws Exception {
        String corps = "{\"titre\":\"Concert\",\"debuteLe\":\"" + Instant.now().plus(30, ChronoUnit.DAYS)
            + "\",\"venueId\":\"" + creerLieu(jeton) + "\"}";
        String reponse = mvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, jeton)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("BROUILLON"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    // ---- /api/auth ----

    /** Le chemin complet : inscription, connexion, puis /me avec le vrai jeton. */
    @Test
    void inscription_connexion_et_me_avec_un_vrai_jeton() throws Exception {
        String email = emailUnique();
        inscrire(email);
        String reponse = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.refreshToken").exists())
            .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(reponse, "$.accessToken")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("ORGANISATEUR"));
    }

    /** Correctif /register : un champ « role » envoyé est ignoré, le compte est toujours ORGANISATEUR. */
    @Test
    void l_inscription_ignore_un_role_envoye_par_le_client() throws Exception {
        String email = emailUnique();

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\",\"nomComplet\":\"X\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email)).isEqualTo("ORGANISATEUR");
    }

    /** Bornes du schéma V2 et de BCrypt : 400, jamais 500. */
    @Test
    void l_inscription_refuse_en_400_les_valeurs_hors_bornes_et_un_email_deja_pris() throws Exception {
        String emailTropLong = "x".repeat(250) + "@billetplus.sn";
        for (String corps : new String[]{
                "{\"email\":\"" + emailTropLong + "\",\"motDePasse\":\"motdepasse\",\"nomComplet\":\"X\"}",
                "{\"email\":\"" + emailUnique() + "\",\"motDePasse\":\"court\",\"nomComplet\":\"X\"}",
                "{\"email\":\"" + emailUnique() + "\",\"motDePasse\":\"" + "x".repeat(73) + "\",\"nomComplet\":\"X\"}",
                "{\"email\":\"pas-un-email\",\"motDePasse\":\"motdepasse\",\"nomComplet\":\"X\"}"}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
        }
        String email = emailUnique();
        inscrire(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"motdepasse\",\"nomComplet\":\"X\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void la_connexion_refuse_en_401_un_mauvais_mot_de_passe_et_me_exige_un_jeton() throws Exception {
        String email = emailUnique();
        inscrire(email);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"mauvais-mot-de-passe\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer jeton-invente"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void le_rafraichissement_donne_un_nouveau_jeton_utilisable() throws Exception {
        String email = emailUnique();
        inscrire(email);
        String connexion = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
            .andReturn().getResponse().getContentAsString();

        String rafraichi = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + JsonPath.read(connexion, "$.refreshToken") + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(rafraichi, "$.accessToken")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email));
    }

    // ---- /api/events ----

    /** Un brouillon n'est visible que de son organisateur ; tous les autres reçoivent le 404 d'un identifiant inconnu. */
    @Test
    void un_brouillon_est_invisible_des_autres_et_visible_de_son_organisateur() throws Exception {
        String orga = organisateurConnecte();
        String eventId = creerEvenement(orga);
        String message = "Événement introuvable : " + eventId;

        mvc.perform(get("/api/events/{id}", eventId))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(message));
        mvc.perform(get("/api/events/{id}", eventId).header(HttpHeaders.AUTHORIZATION, organisateurConnecte()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(message));
        mvc.perform(get("/api/events/{id}", eventId).header(HttpHeaders.AUTHORIZATION, orga))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BROUILLON"));
    }

    /**
     * Décision de cette branche : un non-propriétaire reçoit 404 sur le brouillon
     * d'un autre (avant : 403, qui révélait son existence), et 403 sur un
     * événement publié, dont l'existence est publique.
     */
    @Test
    void un_autre_organisateur_recoit_404_sur_un_brouillon_et_403_sur_un_evenement_publie() throws Exception {
        String orga = organisateurConnecte();
        String autre = organisateurConnecte();
        String eventId = creerEvenement(orga);

        mvc.perform(post("/api/events/{id}/publier", eventId).header(HttpHeaders.AUTHORIZATION, autre))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Événement introuvable : " + eventId));
        mvc.perform(post("/api/events/{id}/annuler", eventId).header(HttpHeaders.AUTHORIZATION, autre))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/events/{id}/publier", eventId).header(HttpHeaders.AUTHORIZATION, orga))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLIE"));
        mvc.perform(post("/api/events/{id}/annuler", eventId).header(HttpHeaders.AUTHORIZATION, autre))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/events/{id}", eventId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLIE"));
    }

    @Test
    void publier_deux_fois_repond_409_et_annuler_un_evenement_publie_reussit() throws Exception {
        String orga = organisateurConnecte();
        String eventId = creerEvenement(orga);

        mvc.perform(post("/api/events/{id}/publier", eventId).header(HttpHeaders.AUTHORIZATION, orga))
            .andExpect(status().isOk());
        mvc.perform(post("/api/events/{id}/publier", eventId).header(HttpHeaders.AUTHORIZATION, orga))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/events/{id}/annuler", eventId).header(HttpHeaders.AUTHORIZATION, orga))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANNULE"));
    }

    /** /mine : uniquement ses propres événements (l'organisateur vient du jeton, jamais du corps). */
    @Test
    void mine_ne_liste_que_ses_propres_evenements() throws Exception {
        String orga = organisateurConnecte();
        String autre = organisateurConnecte();
        String sien = creerEvenement(orga);
        creerEvenement(autre);

        mvc.perform(get("/api/events/mine").header(HttpHeaders.AUTHORIZATION, orga))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(sien));
    }

    @Test
    void creer_un_evenement_exige_un_organisateur_un_lieu_existant_et_un_titre_borne() throws Exception {
        String orga = organisateurConnecte();
        String venueId = creerLieu(orga);
        String debut = Instant.now().plus(30, ChronoUnit.DAYS).toString();

        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Concert\",\"debuteLe\":\"" + debut + "\",\"venueId\":\"" + venueId + "\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, orga).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"" + "x".repeat(256) + "\",\"debuteLe\":\"" + debut + "\",\"venueId\":\"" + venueId + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, orga).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Concert\",\"debuteLe\":\"" + debut + "\",\"venueId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isNotFound());
    }

    // ---- /api/venues ----

    @Test
    void un_lieu_se_cree_avec_un_jeton_et_se_lit_sans_jeton() throws Exception {
        String orga = organisateurConnecte();
        String venueId = creerLieu(orga);

        mvc.perform(get("/api/venues/{id}", venueId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nom").value("Grand Theatre"));
        mvc.perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"X\",\"adresse\":\"Dakar\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/venues").header(HttpHeaders.AUTHORIZATION, orga).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"X\",\"adresse\":\"Dakar\",\"latitude\":91}"))
            .andExpect(status().isBadRequest());
    }

    /** /error doit rester public : sans cela, un 404 deviendrait un 403 pour un anonyme (Spring Security 6). */
    @Test
    void une_route_inconnue_repond_404_et_non_403_a_un_visiteur_anonyme() throws Exception {
        mvc.perform(get("/api/venues/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }
}
