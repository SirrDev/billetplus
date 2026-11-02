package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
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
 * GET /api/events/affectes : un agent voit les événements qu'il peut contrôler, et seulement
 * ceux-là. Contexte complet (vrai PostgreSQL), affectation faite par la vraie route.
 */
class EvenementsAffectesHttpIntegrationTest extends ContexteCompletTestBase {

    @Autowired
    private AuthService authService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private EventService eventService;

    private UUID organisateur() {
        return authService.registerOrganisateur("orga-" + UUID.randomUUID() + "@billetplus.sn", "motdepasse", "Orga").getId();
    }

    private UUID agentDe(UUID organisateur) throws Exception {
        String reponse = mvc.perform(post("/api/agents").with(connecte(organisateur, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"agent-" + UUID.randomUUID() + "@billetplus.sn\",\"motDePasse\":\"motdepasse\",\"nomComplet\":\"Agent\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(reponse, "$.id"));
    }

    private Event evenement(UUID organisateur, String titre, Instant debut, Instant fin) {
        UUID lieu = venueService.create("Sorano " + titre, "Dakar", null, null).getId();
        return eventService.create(titre, null, debut, fin, lieu, organisateur, null);
    }

    private void affecter(UUID organisateur, Event e, UUID agent) throws Exception {
        mvc.perform(put("/api/events/{id}/agents/{agentId}", e.getId(), agent).with(connecte(organisateur, "ORGANISATEUR")))
            .andExpect(status().isNoContent());
    }

    @Test
    void l_agent_voit_ses_evenements_publies_a_venir_ou_en_cours_du_plus_proche_au_plus_lointain() throws Exception {
        UUID orga = organisateur();
        UUID agent = agentDe(orga);
        Instant maintenant = Instant.now();

        Event lointain = evenement(orga, "Lointain", maintenant.plus(20, ChronoUnit.DAYS), null);
        Event proche = evenement(orga, "Proche", maintenant.plus(2, ChronoUnit.DAYS), null);
        // Commencé il y a 3 h, sans heure de fin : portes encore ouvertes.
        Event enCours = evenement(orga, "EnCours", maintenant.minus(3, ChronoUnit.HOURS), null);
        Event brouillon = evenement(orga, "Brouillon", maintenant.plus(5, ChronoUnit.DAYS), null);
        Event annule = evenement(orga, "Annule", maintenant.plus(6, ChronoUnit.DAYS), null);
        Event nonAffecte = evenement(orga, "NonAffecte", maintenant.plus(7, ChronoUnit.DAYS), null);
        for (Event e : List.of(lointain, proche, enCours, annule, nonAffecte)) {
            eventService.publier(e.getId(), orga);
        }
        for (Event e : List.of(lointain, proche, enCours, brouillon, annule)) {
            affecter(orga, e, agent);
        }
        eventService.annuler(annule.getId(), orga);

        String reponse = mvc.perform(get("/api/events/affectes").with(connecte(agent, "AGENT")))
            .andExpect(status().isOk())
            // Vue publique : ni organisateur ni date de création.
            .andExpect(jsonPath("$[0].organizerId").doesNotExist())
            .andExpect(jsonPath("$[0].venueNom").value("Sorano EnCours"))
            .andReturn().getResponse().getContentAsString();

        List<String> titres = JsonPath.read(reponse, "$[*].titre");
        assertThat(titres).containsExactly("EnCours", "Proche", "Lointain");
    }

    @Test
    void un_evenement_commence_depuis_plus_d_un_jour_sans_fin_connue_n_est_plus_liste() throws Exception {
        UUID orga = organisateur();
        UUID agent = agentDe(orga);
        Instant maintenant = Instant.now();
        Event passe = evenement(orga, "Passe", maintenant.minus(30, ChronoUnit.HOURS), null);
        // Commencé hier, mais fin encore à venir : toujours à contrôler.
        Event festival = evenement(orga, "Festival", maintenant.minus(30, ChronoUnit.HOURS), maintenant.plus(2, ChronoUnit.DAYS));
        for (Event e : List.of(passe, festival)) {
            eventService.publier(e.getId(), orga);
            affecter(orga, e, agent);
        }

        String reponse = mvc.perform(get("/api/events/affectes").with(connecte(agent, "AGENT")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> titres = JsonPath.read(reponse, "$[*].titre");
        assertThat(titres).containsExactly("Festival");
    }

    @Test
    void un_autre_agent_ne_voit_pas_les_affectations_d_un_collegue() throws Exception {
        UUID orga = organisateur();
        UUID agent = agentDe(orga);
        UUID collegue = agentDe(orga);
        Event e = evenement(orga, "Concert", Instant.now().plus(3, ChronoUnit.DAYS), null);
        eventService.publier(e.getId(), orga);
        affecter(orga, e, agent);

        mvc.perform(get("/api/events/affectes").with(connecte(collegue, "AGENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void la_liste_exige_un_jeton_d_agent() throws Exception {
        // Sans jeton : 401. Vérifié aussi sans la règle de SecurityConfig (GlobalExceptionHandler
        // répond 401 à un anonyme refusé par @PreAuthorize) : ce test ne prouve donc que le résultat.
        mvc.perform(get("/api/events/affectes")).andExpect(status().isUnauthorized());
        // Un organisateur n'est pas un agent : il contrôle ses événements depuis /mine.
        mvc.perform(get("/api/events/affectes").with(connecte(organisateur(), "ORGANISATEUR")))
            .andExpect(status().isForbidden());
    }
}
