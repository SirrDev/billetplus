package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.CategorieEvenement;
import com.billetplus.events.domain.Event;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catégories d'événements (V13) : choisies à la création et à la modification, exposées
 * dans les DTO, et filtre du catalogue public GET /api/events?categorie=. Contexte complet.
 */
class CategoriesEvenementsHttpIntegrationTest extends ContexteCompletTestBase {

    @Autowired
    private AuthService authService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private EventService eventService;

    private UUID organisateur() {
        return authService.registerOrganisateur("orga-" + UUID.randomUUID() + "@billetplus.sn", "motdepasse", "Orga").getId();
    }

    private Event publie(UUID orga, String titre, CategorieEvenement categorie) {
        UUID lieu = venueService.create("Lieu " + titre, "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        Event e = eventService.create(titre, null, Instant.now().plus(5, ChronoUnit.DAYS), null, lieu, orga, categorie);
        organisateurVerifie(orga);
        eventService.publier(e.getId(), orga);
        return e;
    }

    @Test
    void le_catalogue_se_filtre_par_categorie() throws Exception {
        UUID orga = organisateur();
        String marque = UUID.randomUUID().toString().substring(0, 8);
        publie(orga, "Concert " + marque, CategorieEvenement.MUSIQUE);
        publie(orga, "Derby " + marque, CategorieEvenement.SPORT);
        publie(orga, "Forum " + marque, CategorieEvenement.CONFERENCE);

        String sport = mvc.perform(get("/api/events").param("q", marque).param("categorie", "sport"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> titres = JsonPath.read(sport, "$.contenu[*].titre");
        assertThat(titres).containsExactly("Derby " + marque);
        assertThat(JsonPath.<String>read(sport, "$.contenu[0].categorie")).isEqualTo("SPORT");

        String tous = mvc.perform(get("/api/events").param("q", marque))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(tous, "$.contenu[*].titre")).hasSize(3);
    }

    @Test
    void une_categorie_inconnue_repond_400() throws Exception {
        mvc.perform(get("/api/events").param("categorie", "cinema"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void la_categorie_se_choisit_a_la_creation_et_se_modifie_meme_publie() throws Exception {
        UUID orga = organisateur();
        UUID lieu = venueService.create("Sorano " + UUID.randomUUID(), "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        String debut = Instant.now().plus(9, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();

        String cree = mvc.perform(post("/api/events").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Gala\",\"debuteLe\":\"" + debut + "\",\"venueId\":\"" + lieu + "\",\"categorie\":\"SPECTACLE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categorie").value("SPECTACLE"))
            .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(cree, "$.id"));
        organisateurVerifie(orga);
        eventService.publier(id, orga);

        mvc.perform(put("/api/events/{id}", id).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Gala\",\"debuteLe\":\"" + debut + "\",\"categorie\":\"FESTIVAL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categorie").value("FESTIVAL"));
        // Sans catégorie dans le corps : inchangée.
        mvc.perform(put("/api/events/{id}", id).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Gala 2\",\"debuteLe\":\"" + debut + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categorie").value("FESTIVAL"));
    }

    @Test
    void sans_categorie_a_la_creation_l_evenement_est_classe_autre() throws Exception {
        UUID orga = organisateur();
        UUID lieu = venueService.create("Sorano " + UUID.randomUUID(), "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        mvc.perform(post("/api/events").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Divers\",\"debuteLe\":\"" + Instant.now().plus(9, ChronoUnit.DAYS) + "\",\"venueId\":\"" + lieu + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categorie").value("AUTRE"));
        // Valeur hors liste dans le corps : 400, pas 500.
        mvc.perform(post("/api/events").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Divers\",\"debuteLe\":\"" + Instant.now().plus(9, ChronoUnit.DAYS) + "\",\"venueId\":\"" + lieu + "\",\"categorie\":\"CINEMA\"}"))
            .andExpect(status().isBadRequest());
    }
}
