package com.billetplus;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/events : liste publique et recherche (branche feature/backend-events-public-listing).
 *
 * Les données sont insérées en SQL : l'API refuse de créer un événement passé,
 * terminé ou annulé d'emblée, or ce sont justement les cas à exclure de la liste.
 *
 * La base est partagée par toutes les classes du contexte complet : chaque test
 * place un marqueur unique dans ses titres et filtre dessus (q), pour ne voir
 * que ses propres événements, quel que soit l'ordre d'exécution.
 */
class ListePubliqueEvenementsHttpIntegrationTest extends ContexteCompletTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    private static String marqueur() {
        return "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private UUID creerLieu() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO venues (id, nom, adresse, ville_code) VALUES (?, 'Grand Theatre', 'Dakar Plateau', 'SN-DAKAR')", id);
        return id;
    }

    private UUID inserer(String titre, String statut, Instant debut, Instant fin, UUID lieu) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, titre, debute_le, termine_le, venue_id, organizer_id, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
            id, titre, Timestamp.from(debut), fin == null ? null : Timestamp.from(fin), lieu, UUID.randomUUID(), statut);
        return id;
    }

    private static Instant dans(long heures) {
        return Instant.now().plus(heures, ChronoUnit.HOURS);
    }

    @Test
    void un_anonyme_ne_voit_que_les_evenements_publies_a_venir_ou_en_cours_tries_par_date() throws Exception {
        String m = marqueur();
        UUID lieu = creerLieu();
        UUID aVenir = inserer("Concert " + m, "PUBLIE", dans(48), null, lieu);
        UUID enCours = inserer("Festival " + m, "PUBLIE", dans(-1), dans(2), lieu);
        inserer("Passe " + m, "PUBLIE", dans(-5), dans(-1), lieu);
        inserer("Passe sans fin " + m, "PUBLIE", dans(-1), null, lieu);
        inserer("Brouillon " + m, "BROUILLON", dans(48), null, lieu);
        inserer("Annule " + m, "ANNULE", dans(48), null, lieu);
        inserer("Termine " + m, "TERMINE", dans(48), null, lieu);

        mvc.perform(get("/api/events").param("q", m))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.pages").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.taille").value(20))
            // Le plus proche d'abord : celui qui a déjà commencé, puis celui à venir.
            .andExpect(jsonPath("$.contenu[0].id").value(enCours.toString()))
            .andExpect(jsonPath("$.contenu[1].id").value(aVenir.toString()))
            .andExpect(jsonPath("$.contenu[0].venueNom").value("Grand Theatre"))
            .andExpect(jsonPath("$.contenu[0].venueAdresse").value("Dakar Plateau"))
            // DTO public : ni organisateur ni statut interne.
            .andExpect(jsonPath("$.contenu[0].organizerId").doesNotExist())
            .andExpect(jsonPath("$.contenu[0].status").doesNotExist());
    }

    @Test
    void la_recherche_ignore_la_casse_et_traite_les_jokers_comme_des_caracteres_ordinaires() throws Exception {
        String m = marqueur();
        UUID lieu = creerLieu();
        UUID pourcent = inserer("Soiree " + m + " 100% Mbalax", "PUBLIE", dans(24), null, lieu);
        inserer("Soiree " + m + " 1000 Mbalax", "PUBLIE", dans(25), null, lieu);
        UUID souligne = inserer("Gala " + m + "_a", "PUBLIE", dans(26), null, lieu);
        inserer("Gala " + m + "ba", "PUBLIE", dans(27), null, lieu);

        mvc.perform(get("/api/events").param("q", m.toUpperCase()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(4));
        // Sans échappement, « 100% » correspondrait aussi à « 1000 ».
        mvc.perform(get("/api/events").param("q", m + " 100%"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenu[0].id").value(pourcent.toString()));
        // Sans échappement, « _ » correspondrait à n'importe quel caractère (« b »).
        mvc.perform(get("/api/events").param("q", m + "_"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenu[0].id").value(souligne.toString()));
        // Les espaces autour sont ignorés.
        mvc.perform(get("/api/events").param("q", "  " + m + "  "))
            .andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void la_pagination_decoupe_les_resultats_dans_l_ordre() throws Exception {
        String m = marqueur();
        UUID lieu = creerLieu();
        UUID premier = inserer("Match " + m + " 1", "PUBLIE", dans(10), null, lieu);
        UUID deuxieme = inserer("Match " + m + " 2", "PUBLIE", dans(20), null, lieu);
        UUID troisieme = inserer("Match " + m + " 3", "PUBLIE", dans(30), null, lieu);

        String page0 = mvc.perform(get("/api/events").param("q", m).param("taille", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.pages").value(2))
            .andReturn().getResponse().getContentAsString();
        String page1 = mvc.perform(get("/api/events").param("q", m).param("taille", "2").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andReturn().getResponse().getContentAsString();

        List<String> ids0 = JsonPath.read(page0, "$.contenu[*].id");
        List<String> ids1 = JsonPath.read(page1, "$.contenu[*].id");
        assertThat(ids0).containsExactly(premier.toString(), deuxieme.toString());
        assertThat(ids1).containsExactly(troisieme.toString());

        // Au-delà de la dernière page : liste vide, pas d'erreur.
        mvc.perform(get("/api/events").param("q", m).param("taille", "2").param("page", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenu").isEmpty())
            .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void les_parametres_hors_bornes_repondent_400() throws Exception {
        mvc.perform(get("/api/events").param("taille", "0"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("taille", "51"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("taille doit être comprise entre 1 et 50"));
        mvc.perform(get("/api/events").param("page", "-1"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("page", "abc"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("q", "a".repeat(101)))
            .andExpect(status().isBadRequest());

        // Les bornes elles-mêmes sont acceptées.
        mvc.perform(get("/api/events").param("taille", "50").param("q", "a".repeat(100)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taille").value(50));
    }

    /** Sans q : toute la liste publique, première page de 20 par défaut, sans jeton. */
    @Test
    void sans_recherche_la_liste_est_publique_et_paginee_par_defaut() throws Exception {
        String m = marqueur();
        inserer("Theatre " + m, "PUBLIE", dans(3), null, creerLieu());

        String reponse = mvc.perform(get("/api/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taille").value(20))
            .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(reponse, "$.contenu[*].id");
        long total = ((Number) JsonPath.read(reponse, "$.total")).longValue();
        assertThat(ids).hasSizeLessThanOrEqualTo(20);
        assertThat(total).isGreaterThanOrEqualTo(1);
    }
}
