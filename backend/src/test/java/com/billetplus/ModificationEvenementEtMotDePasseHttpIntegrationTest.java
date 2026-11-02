package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.events.application.EventRepository;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/events/{id} et POST /api/auth/mot-de-passe, contexte complet (vrai PostgreSQL,
 * vrai BCrypt). Chaque test crée ses comptes : la règle de débit du mot de passe compte
 * par compte.
 */
class ModificationEvenementEtMotDePasseHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String DEBUT = "2026-12-05T20:00:00Z";
    private static final String FIN = "2026-12-06T02:00:00Z";

    @Autowired
    private AuthService authService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private EventService eventService;
    @Autowired
    private EventRepository eventRepository;

    private UUID organisateur(String email, String motDePasse) {
        return authService.registerOrganisateur(email, motDePasse, "Orga").getId();
    }

    private UUID organisateur() {
        return organisateur("orga-" + UUID.randomUUID() + "@billetplus.sn", "motdepasse");
    }

    private Event brouillon(UUID orga) {
        UUID lieu = venueService.create("Sorano " + UUID.randomUUID(), "Dakar", null, null).getId();
        return eventService.create("Concert", "Avant", Instant.parse(DEBUT), Instant.parse(FIN), lieu, orga, null);
    }

    private static String corps(String titre, String debut, String fin) {
        return "{\"titre\":\"" + titre + "\",\"description\":\"Apres\",\"debuteLe\":\"" + debut + "\""
            + (fin == null ? "" : ",\"termineLe\":\"" + fin + "\"") + "}";
    }

    // ---------------------------------------------------------------- modification d'un événement

    @Test
    void l_organisateur_modifie_son_brouillon_dates_comprises() throws Exception {
        UUID orga = organisateur();
        Event e = brouillon(orga);

        mvc.perform(put("/api/events/{id}", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(corps("Grand concert", "2026-12-12T20:00:00Z", null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titre").value("Grand concert"))
            .andExpect(jsonPath("$.description").value("Apres"))
            .andExpect(jsonPath("$.debuteLe").value("2026-12-12T20:00:00Z"))
            .andExpect(jsonPath("$.termineLe").doesNotExist())
            .andExpect(jsonPath("$.status").value("BROUILLON"));
        assertThat(eventService.get(e.getId()).getTitre()).isEqualTo("Grand concert");
    }

    @Test
    void un_evenement_publie_garde_ses_dates() throws Exception {
        UUID orga = organisateur();
        Event e = brouillon(orga);
        eventService.publier(e.getId(), orga);

        mvc.perform(put("/api/events/{id}", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(corps("Titre corrige", DEBUT, FIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titre").value("Titre corrige"));

        mvc.perform(put("/api/events/{id}", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(corps("Report", "2026-12-05T21:00:00Z", FIN)))
            .andExpect(status().isConflict());
        assertThat(eventService.get(e.getId()).getTitre()).isEqualTo("Titre corrige");
    }

    @Test
    void un_autre_organisateur_ne_modifie_rien() throws Exception {
        UUID orga = organisateur();
        Event e = brouillon(orga);
        UUID autre = organisateur();

        // Brouillon d'un autre : 404, comme un identifiant inconnu.
        mvc.perform(put("/api/events/{id}", e.getId()).with(connecte(autre, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(corps("Pirate", DEBUT, FIN)))
            .andExpect(status().isNotFound());
        // Publié, donc d'existence publique : 403.
        eventService.publier(e.getId(), orga);
        mvc.perform(put("/api/events/{id}", e.getId()).with(connecte(autre, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(corps("Pirate", DEBUT, FIN)))
            .andExpect(status().isForbidden());
        assertThat(eventService.get(e.getId()).getTitre()).isEqualTo("Concert");
    }

    @Test
    void la_modification_exige_un_jeton_et_un_corps_valide() throws Exception {
        UUID orga = organisateur();
        Event e = brouillon(orga);

        mvc.perform(put("/api/events/{id}", e.getId())
                .contentType(MediaType.APPLICATION_JSON).content(corps("Anonyme", DEBUT, FIN)))
            .andExpect(status().isUnauthorized());
        // Titre au-delà de VARCHAR(255) : 400 de validation, pas 500 de PostgreSQL.
        mvc.perform(put("/api/events/{id}", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(corps("x".repeat(256), DEBUT, FIN)))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/events/{id}", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(corps("Concert", FIN, DEBUT)))
            .andExpect(status().isBadRequest());
        assertThat(eventService.get(e.getId()).getTitre()).isEqualTo("Concert");
    }

    @Test
    void une_copie_perimee_ne_peut_pas_annuler_une_annulation() {
        UUID orga = organisateur();
        Event e = brouillon(orga);
        eventService.publier(e.getId(), orga);
        // Lue avant l'annulation, comme par une modification concurrente.
        Event perimee = eventService.get(e.getId());
        eventService.annuler(e.getId(), orga);

        perimee.modifier("Titre ecrase", null, Instant.parse(DEBUT), Instant.parse(FIN), null);
        assertThatThrownBy(() -> eventRepository.save(perimee))
            .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(eventService.get(e.getId()).getStatus().name()).isEqualTo("ANNULE");
    }

    // ---------------------------------------------------------------- changement de mot de passe

    @Test
    void le_nouveau_mot_de_passe_remplace_l_ancien_a_la_connexion() throws Exception {
        String email = "mdp-" + UUID.randomUUID() + "@billetplus.sn";
        UUID orga = organisateur(email, "ancienMotDePasse");

        mvc.perform(post("/api/auth/mot-de-passe").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motDePasseActuel\":\"ancienMotDePasse\",\"nouveauMotDePasse\":\"nouveauMotDePasse\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"ancienMotDePasse\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"nouveauMotDePasse\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void un_mot_de_passe_actuel_faux_repond_400_et_non_401() throws Exception {
        String email = "mdp-" + UUID.randomUUID() + "@billetplus.sn";
        UUID orga = organisateur(email, "ancienMotDePasse");

        mvc.perform(post("/api/auth/mot-de-passe").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motDePasseActuel\":\"mauvaisMotDePasse\",\"nouveauMotDePasse\":\"nouveauMotDePasse\"}"))
            .andExpect(status().isBadRequest());
        // Trop court : refusé par la validation.
        mvc.perform(post("/api/auth/mot-de-passe").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motDePasseActuel\":\"ancienMotDePasse\",\"nouveauMotDePasse\":\"court\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"ancienMotDePasse\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void le_changement_exige_un_jeton() throws Exception {
        mvc.perform(post("/api/auth/mot-de-passe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motDePasseActuel\":\"ancienMotDePasse\",\"nouveauMotDePasse\":\"nouveauMotDePasse\"}"))
            .andExpect(status().isUnauthorized());
    }
}
