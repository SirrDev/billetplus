package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.auth.application.UserRepository;
import com.billetplus.auth.domain.User;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Event;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Restes de la relecture de feature/organisateur-carte-equipe, sur un vrai PostgreSQL :
 * affectation restée après un retrait, doublon d'e-mail qui n'apparaît qu'à l'écriture.
 */
class SuitesRelectureEquipeHttpIntegrationTest extends ContexteCompletTestBase {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VenueService venueService;
    @Autowired
    private EventService eventService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

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

    private Event evenementPublie(UUID organisateur) {
        UUID lieu = venueService.create("Sorano", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        Event e = eventService.create("Concert", null, Instant.now().plus(5, ChronoUnit.DAYS), null, lieu, organisateur, null);
        organisateurVerifie(organisateur);
        eventService.publier(e.getId(), organisateur);
        return e;
    }

    /**
     * Course affectation / retrait reproduite à la main : le compte est désactivé SANS que ses
     * affectations soient supprimées, comme si l'affectation s'était écrite après le retrait.
     */
    @Test
    void un_agent_retire_ne_scanne_plus_meme_avec_une_affectation_restee() throws Exception {
        UUID orga = organisateur();
        UUID agent = agentDe(orga);
        Event e = evenementPublie(orga);
        mvc.perform(put("/api/events/{id}/agents/{a}", e.getId(), agent).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isNoContent());
        String corps = "{\"qrContent\":\"faux-qr\"}";

        // Avant : l'agent affecté voit son événement (le scan lui-même dépend du QR, simulé ici).
        mvc.perform(get("/api/events/affectes").with(connecte(agent, "AGENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        jdbc.update("UPDATE users SET desactive_le = now() WHERE id = ?", agent);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_agents WHERE agent_id = ?", Integer.class, agent))
            .isEqualTo(1);

        mvc.perform(post("/api/events/{id}/scans", e.getId()).with(connecte(agent, "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(corps))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/events/affectes").with(connecte(agent, "AGENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        // Ni listé parmi les affectés de l'événement.
        mvc.perform(get("/api/events/{id}/agents", e.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * Doublon qui a échappé à existsByEmail (deux requêtes simultanées) : il n'apparaît qu'à
     * l'écriture. Ce test l'y amène directement, sans passer par le contrôle préalable.
     */
    @Test
    void un_doublon_d_email_decouvert_a_l_ecriture_donne_le_meme_refus_qu_un_doublon_ordinaire() {
        String email = "double-" + UUID.randomUUID() + "@billetplus.sn";
        authService.registerOrganisateur(email, "motdepasse", "Premier");
        User second = User.agentCreePar(organisateur(), email, "hash", "Second");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                statut -> userRepository.save(second)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un compte existe déjà avec cet email");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isEqualTo(1);
    }

    /**
     * Doublon découvert à l'écriture d'une MODIFICATION (entité existante, merge puis flush) :
     * même refus. La partie HTTP vérifie le contrôle préalable ; la vraie course à deux
     * requêtes simultanées n'est pas reproduite.
     */
    @Test
    void une_modification_vers_un_email_pris_est_refusee_a_l_ecriture_et_par_la_route() throws Exception {
        UUID orga = organisateur();
        UUID agent = agentDe(orga);
        String pris = "pris-" + UUID.randomUUID() + "@billetplus.sn";
        authService.registerOrganisateur(pris, "motdepasse", "Pris");
        User lu = userRepository.findById(agent).orElseThrow();
        lu.modifierIdentite(pris, "Agent");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                statut -> userRepository.save(lu)))
            .isInstanceOf(IllegalArgumentException.class);

        // Par la route : le contrôle préalable répond le même 400.
        mvc.perform(put("/api/agents/{id}", agent).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + pris + "\",\"nomComplet\":\"Agent\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Un compte existe déjà avec cet email"));
    }
}
