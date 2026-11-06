package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/events/{id} : l'identifiant de l'organisateur n'est rendu qu'à lui ; double annulation 409. */
class OrganisateurMasqueHttpIntegrationTest extends ContexteCompletTestBase {

    @Autowired
    private AuthService authService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private EventService eventService;

    private UUID organisateur() {
        return authService.registerOrganisateur("orga-" + UUID.randomUUID() + "@billetplus.sn", "motdepasse", "Orga").getId();
    }

    private Event evenementPublie(UUID orga) {
        UUID lieu = venueService.create("Sorano", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        Event e = eventService.create("Concert", null, Instant.now().plus(3, ChronoUnit.DAYS), null, lieu, orga, null);
        eventService.publier(e.getId(), orga);
        return e;
    }

    @Test
    void seul_l_organisateur_recoit_son_identifiant() throws Exception {
        UUID orga = organisateur();
        Event e = evenementPublie(orga);

        mvc.perform(get("/api/events/{id}", e.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titre").value("Concert"))
            .andExpect(jsonPath("$.organizerId").value(nullValue()));
        mvc.perform(get("/api/events/{id}", e.getId()).with(connecte(organisateur(), "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizerId").value(nullValue()));
        mvc.perform(get("/api/events/{id}", e.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizerId").value(orga.toString()));
    }

    @Test
    void annuler_deux_fois_repond_409_sans_rejouer_l_annulation() throws Exception {
        UUID orga = organisateur();
        Event e = evenementPublie(orga);

        mvc.perform(post("/api/events/{id}/annuler", e.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANNULE"));
        mvc.perform(post("/api/events/{id}/annuler", e.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("L'événement est déjà annulé"));
    }
}
