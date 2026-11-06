package com.billetplus;

import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Event;
import com.billetplus.shared.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Villes et pays des lieux (ADR 0011) sur un vrai PostgreSQL : liste publique remplie par
 * V18, lieu créé avec sa ville, recherche par ville et par pays, publication refusée hors
 * du Sénégal.
 */
class VillesHttpIntegrationTest extends ContexteCompletTestBase {

    @Autowired
    private VenueService venueService;
    @Autowired
    private EventService eventService;

    private final UUID orga = UUID.randomUUID();

    private Event evenement(String titre, String villeCode) {
        UUID lieu = venueService.create("Salle " + titre, "Centre-ville", null, null, villeCode).getId();
        return eventService.create(titre, null, Instant.now().plus(10, ChronoUnit.DAYS), null, lieu, orga, null);
    }

    @Test
    void la_liste_des_villes_est_publique_groupee_par_pays_capitale_d_abord() throws Exception {
        mvc.perform(get("/api/villes"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "max-age=3600, public"))
            .andExpect(jsonPath("$", hasSize(8)))
            .andExpect(jsonPath("$[0].code").value("SN"))
            .andExpect(jsonPath("$[0].nom").value("Sénégal"))
            .andExpect(jsonPath("$[0].villes[0].code").value("SN-DAKAR"))
            .andExpect(jsonPath("$[0].villes[1].nom").value("Thiès"))
            .andExpect(jsonPath("$[1].code").value("CI"))
            .andExpect(jsonPath("$[1].villes[0].nom").value("Abidjan"))
            .andExpect(jsonPath("$[7].villes[0].code").value("GW-BISSAU"));
    }

    @Test
    void un_lieu_se_cree_avec_une_ville_de_la_liste_seulement() throws Exception {
        mvc.perform(post("/api/venues").with(connecte(orga, "ORGANISATEUR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Stade Lat Dior\",\"adresse\":\"Route de Dakar\",\"villeCode\":\"SN-THIES\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.villeCode").value("SN-THIES"))
            .andExpect(jsonPath("$.villeNom").value("Thiès"))
            .andExpect(jsonPath("$.pays").value("SN"));

        mvc.perform(post("/api/venues").with(connecte(orga, "ORGANISATEUR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Salle\",\"adresse\":\"Rue 1\",\"villeCode\":\"SN-PARIS\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Ville inconnue"));
        mvc.perform(post("/api/venues").with(connecte(orga, "ORGANISATEUR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Salle\",\"adresse\":\"Rue 1\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void la_recherche_filtre_par_ville_et_par_pays() throws Exception {
        String marque = "Festival " + UUID.randomUUID().toString().substring(0, 8);
        Event dakar = evenement(marque + " Dakar", "SN-DAKAR");
        Event thies = evenement(marque + " Thies", "SN-THIES");
        eventService.publier(dakar.getId(), orga);
        eventService.publier(thies.getId(), orga);

        mvc.perform(get("/api/events").param("q", marque).param("ville", "SN-THIES"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenu", hasSize(1)))
            .andExpect(jsonPath("$.contenu[0].id").value(thies.getId().toString()))
            .andExpect(jsonPath("$.contenu[0].villeNom").value("Thiès"))
            .andExpect(jsonPath("$.contenu[0].pays").value("SN"));
        mvc.perform(get("/api/events").param("q", marque).param("pays", "SN"))
            .andExpect(jsonPath("$.contenu", hasSize(2)));
        mvc.perform(get("/api/events").param("q", marque).param("pays", "CI"))
            .andExpect(jsonPath("$.contenu", hasSize(0)));
        mvc.perform(get("/api/events").param("q", marque))
            .andExpect(jsonPath("$.contenu", hasSize(2)));

        // Casse ignorée, comme pour la catégorie.
        mvc.perform(get("/api/events").param("q", marque).param("ville", "sn-thies").param("pays", "sn"))
            .andExpect(jsonPath("$.contenu", hasSize(1)));
        mvc.perform(get("/api/events").param("ville", "SN-INCONNUE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Ville inconnue"));
        mvc.perform(get("/api/events").param("ville", "X".repeat(5000))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("pays", "FR"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Pays inconnu"));

        mvc.perform(get("/api/events/{id}", dakar.getId()))
            .andExpect(jsonPath("$.villeCode").value("SN-DAKAR"))
            .andExpect(jsonPath("$.villeNom").value("Dakar"))
            .andExpect(jsonPath("$.paysNom").value("Sénégal"));
    }

    @Test
    void un_evenement_hors_du_senegal_se_prepare_mais_ne_se_publie_pas() throws Exception {
        Event abidjan = evenement("Concert Abidjan " + UUID.randomUUID(), "CI-ABIDJAN");

        mvc.perform(post("/api/events/{id}/publier", abidjan.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("La vente hors du Sénégal n'est pas encore ouverte"));
        assertThatThrownBy(() -> eventService.publier(abidjan.getId(), orga))
            .isInstanceOf(BusinessConflictException.class);
        assertThat(eventService.get(abidjan.getId()).getStatus().name()).isEqualTo("BROUILLON");
    }
}
