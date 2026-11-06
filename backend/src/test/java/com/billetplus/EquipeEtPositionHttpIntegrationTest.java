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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retrait d'un agent et position GPS d'un événement, sur un vrai PostgreSQL : ce que les
 * tests unitaires ne voient pas (transaction partagée avec le module events, verrou
 * optimiste V15, lignes venues réellement écrites).
 */
class EquipeEtPositionHttpIntegrationTest extends ContexteCompletTestBase {

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

    private Event evenement(UUID organisateur, Double latitude, Double longitude) {
        UUID lieu = venueService.create("Sorano", "Dakar", latitude, longitude).getId();
        return eventService.create("Concert", null, Instant.now().plus(10, ChronoUnit.DAYS), null, lieu, organisateur, null);
    }

    private int affectationsDe(UUID agent) {
        return jdbc.queryForObject("SELECT count(*) FROM event_agents WHERE agent_id = ?", Integer.class, agent);
    }

    @Test
    void retirer_un_agent_supprime_ses_affectations_dans_la_meme_transaction() throws Exception {
        UUID orga = organisateur();
        UUID agent = agentDe(orga);
        UUID autre = agentDe(orga);
        Event e1 = evenement(orga, null, null);
        Event e2 = evenement(orga, null, null);
        for (Event e : new Event[] {e1, e2}) {
            mvc.perform(put("/api/events/{id}/agents/{a}", e.getId(), agent).with(connecte(orga, "ORGANISATEUR")))
                .andExpect(status().isNoContent());
        }
        mvc.perform(put("/api/events/{id}/agents/{a}", e1.getId(), autre).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isNoContent());

        mvc.perform(delete("/api/agents/{id}", agent).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isNoContent());

        assertThat(affectationsDe(agent)).isZero();
        assertThat(affectationsDe(autre)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT desactive_le IS NOT NULL FROM users WHERE id = ?", Boolean.class, agent)).isTrue();
        // Jeton d'accès encore valide : le compte ne répond plus.
        mvc.perform(get("/api/auth/me").with(connecte(agent, "AGENT"))).andExpect(status().isUnauthorized());
    }

    /**
     * Une écriture partie d'une lecture antérieure au retrait (changement de mot de passe,
     * modification de l'agent) ne doit pas le défaire : avant V15, l'UPDATE complet de la
     * ligne remettait desactive_le à null, sans erreur.
     */
    @Test
    void une_ecriture_concurrente_ne_peut_pas_annuler_un_retrait() throws Exception {
        UUID orga = organisateur();
        UUID agent = agentDe(orga);
        User luAvantLeRetrait = userRepository.findById(agent).orElseThrow();

        mvc.perform(delete("/api/agents/{id}", agent).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isNoContent());

        luAvantLeRetrait.changerMotDePasse("autre-hash");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                statut -> userRepository.save(luAvantLeRetrait)))
            .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(jdbc.queryForObject("SELECT desactive_le IS NOT NULL FROM users WHERE id = ?", Boolean.class, agent)).isTrue();
    }

    @Test
    void une_position_identique_n_ajoute_aucun_lieu_et_une_nouvelle_en_ajoute_un() throws Exception {
        UUID orga = organisateur();
        Event e = evenement(orga, 14.7, -17.4);
        int avant = jdbc.queryForObject("SELECT count(*) FROM venues", Integer.class);

        mvc.perform(put("/api/events/{id}/position", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":14.7,\"longitude\":-17.4}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.venueId").value(e.getVenue().getId().toString()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venues", Integer.class)).isEqualTo(avant);

        mvc.perform(put("/api/events/{id}/position", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":14.71,\"longitude\":-17.4}"))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venues", Integer.class)).isEqualTo(avant + 1);
    }

    /** Au-delà de 30 par heure : 429. Sans limite, PUT /api/agents/{id} servirait à tester des e-mails. */
    @Test
    void modifier_un_agent_et_placer_un_evenement_sont_limites_en_debit() throws Exception {
        UUID orga = organisateur();
        UUID agent = agentDe(orga);
        String email = JsonPath.read(mvc.perform(get("/api/agents").with(connecte(orga, "ORGANISATEUR")))
            .andReturn().getResponse().getContentAsString(), "$[0].email");
        String corps = "{\"email\":\"" + email + "\",\"nomComplet\":\"Agent\"}";
        for (int i = 0; i < 30; i++) {
            mvc.perform(put("/api/agents/{id}", agent).with(connecte(orga, "ORGANISATEUR"))
                    .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());
        }
        mvc.perform(put("/api/agents/{id}", agent).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(corps))
            .andExpect(status().isTooManyRequests());

        Event e = evenement(orga, 14.7, -17.4);
        for (int i = 0; i < 30; i++) {
            mvc.perform(put("/api/events/{id}/position", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":14.7,\"longitude\":-17.4}"))
                .andExpect(status().isOk());
        }
        mvc.perform(put("/api/events/{id}/position", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":14.7,\"longitude\":-17.4}"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void un_evenement_annule_refuse_la_position_sans_ecrire_de_lieu() throws Exception {
        UUID orga = organisateur();
        Event e = evenement(orga, null, null);
        eventService.annuler(e.getId(), orga);
        int avant = jdbc.queryForObject("SELECT count(*) FROM venues", Integer.class);

        mvc.perform(put("/api/events/{id}/position", e.getId()).with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":14.7,\"longitude\":-17.4}"))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venues", Integer.class)).isEqualTo(avant);
    }
}
