package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.EventStatus;
import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.usecase.CancelEventTicketsUseCase;
import com.billetplus.tickets.application.usecase.IssueTicketUseCase;
import com.billetplus.tickets.application.usecase.ReleaseSeatsUseCase;
import com.billetplus.tickets.application.usecase.ReserveSeatsUseCase;
import com.billetplus.tickets.application.usecase.ValidateTicketScanUseCase;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.VenteFermeeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Premier test du contexte COMPLET de l'application (@SpringBootTest), sur un
 * vrai PostgreSQL (PostGIS) et un vrai Redis lancés par Testcontainers.
 *
 * Il vérifie ce que les tests par tranche (@DataJpaTest, @DataRedisTest) ne
 * voient pas : que tous les beans se câblent (ce qui équivaut à un démarrage
 * réel), et que les deux modules collaborent selon l'ADR 0005 (port public
 * StatutEvenements, événement applicatif EvenementAnnule dans la même transaction).
 *
 * Seul le signataire Ed25519 est simulé : la clé privée de dev est gitignorée
 * et absente du runner de CI. Ignoré (et non en échec) sans Docker ; en CI,
 * l'étape « Refuser les tests ignorés » ferait alors échouer le run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ContexteCompletIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockBean
    private QRSigner qrSigner;
    /** Espion sur le vrai cas d'usage : permet de simuler une panne pendant l'annulation. */
    @SpyBean
    private CancelEventTicketsUseCase cancelEventTickets;

    @Autowired
    private ApplicationContext contexte;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private VenueService venueService;
    @Autowired
    private AuthService authService;
    @Autowired
    private EventService eventService;
    @Autowired
    private StatutEvenements statutEvenements;
    @Autowired
    private TicketCategoryRepository categories;
    @Autowired
    private IssueTicketUseCase issueTicket;
    @Autowired
    private ReserveSeatsUseCase reserveSeats;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID organisateur = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn("payload.signature");
    }

    private Event evenement() {
        UUID venueId = venueService.create("Grand Theatre", "Dakar", null, null).getId();
        return eventService.create("Concert", null, Instant.now().plus(30, ChronoUnit.DAYS), null, venueId, organisateur);
    }

    private TicketCategory categoriePour(Event event) {
        TicketCategory category = new TicketCategory(event.getId(), "VIP", Money.ofFcfa(15_000), 100);
        return new TransactionTemplate(transactionManager).execute(s -> categories.save(category));
    }

    private int billetsAuStatut(UUID eventId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM tickets WHERE event_id = ? AND status = ?",
            Integer.class, eventId, status);
    }

    @Test
    void le_contexte_complet_demarre_et_cable_tous_les_cas_d_usage() {
        assertThat(contexte.getBean(IssueTicketUseCase.class)).isNotNull();
        assertThat(contexte.getBean(ReserveSeatsUseCase.class)).isNotNull();
        assertThat(contexte.getBean(ReleaseSeatsUseCase.class)).isNotNull();
        assertThat(contexte.getBean(ValidateTicketScanUseCase.class)).isNotNull();
        assertThat(contexte.getBean(CancelEventTicketsUseCase.class)).isNotNull();
        assertThat(contexte.getBean(StatutEvenements.class)).isNotNull();
    }

    /** Le port public reflète l'état réel de l'événement, du brouillon à l'annulation. */
    @Test
    void le_port_public_suit_le_statut_de_l_evenement() {
        Event event = evenement();
        assertThat(statutEvenements.estOuvertALaVente(event.getId())).isFalse();   // brouillon

        eventService.publier(event.getId(), organisateur);
        assertThat(statutEvenements.estOuvertALaVente(event.getId())).isTrue();

        eventService.annuler(event.getId(), organisateur);
        assertThat(statutEvenements.estOuvertALaVente(event.getId())).isFalse();
    }

    /** Un brouillon est indiscernable d'une catégorie inconnue, jusque dans la réservation (vrai Redis). */
    @Test
    void on_ne_peut_pas_reserver_pour_un_evenement_brouillon() {
        TicketCategory category = categoriePour(evenement());

        assertThatThrownBy(() -> reserveSeats.execute(category.getId(), UUID.randomUUID(), 2))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Catégorie de billet introuvable : " + category.getId());
    }

    /**
     * Le chemin complet de l'ADR 0005 : l'organisateur annule son événement
     * (module events), et les billets vendus (module tickets) passent à ANNULE,
     * puis toute nouvelle émission est refusée.
     */
    @Test
    void annuler_un_evenement_annule_ses_billets_et_ferme_la_vente() {
        Event event = evenement();
        eventService.publier(event.getId(), organisateur);
        TicketCategory category = categoriePour(event);
        issueTicket.execute(category.getId(), UUID.randomUUID(), 3);
        assertThat(billetsAuStatut(event.getId(), "EMIS")).isEqualTo(3);

        eventService.annuler(event.getId(), organisateur);

        assertThat(billetsAuStatut(event.getId(), "EMIS")).isZero();
        assertThat(billetsAuStatut(event.getId(), "ANNULE")).isEqualTo(3);
        assertThatThrownBy(() -> issueTicket.execute(category.getId(), UUID.randomUUID(), 1))
            .isInstanceOf(VenteFermeeException.class);
    }

    /**
     * Atomicité (ADR 0005) : si l'annulation des billets échoue, l'annulation de
     * l'événement est annulée avec elle. Jamais d'événement annulé avec des
     * billets encore valides.
     */
    @Test
    void si_l_annulation_des_billets_echoue_l_evenement_n_est_pas_annule() {
        Event event = evenement();
        eventService.publier(event.getId(), organisateur);
        issueTicket.execute(categoriePour(event).getId(), UUID.randomUUID(), 2);
        doThrow(new IllegalStateException("Panne simulee pendant l'annulation des billets"))
            .when(cancelEventTickets).execute(event.getId());

        assertThatThrownBy(() -> eventService.annuler(event.getId(), organisateur))
            .isInstanceOf(IllegalStateException.class);

        assertThat(eventService.get(event.getId()).getStatus()).isEqualTo(EventStatus.PUBLIE);
        assertThat(billetsAuStatut(event.getId(), "EMIS")).isEqualTo(2);
        assertThat(statutEvenements.estOuvertALaVente(event.getId())).isTrue();
    }

    // ---- HTTP : catégories de billets (premiers tests MockMvc du projet) ----

    /** Même authentification que JwtAuthenticationFilter : principal = UUID, autorité ROLE_<rôle>. */
    private static RequestPostProcessor connecte(UUID userId, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static final String VIP = "{\"nom\":\"VIP\",\"prixFcfa\":15000,\"quotaTotal\":100}";

    @Test
    void l_organisateur_cree_une_categorie_de_son_evenement() throws Exception {
        Event event = evenement();

        mvc.perform(post("/api/events/{id}/categories", event.getId())
                .with(connecte(organisateur, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(VIP))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nom").value("VIP"))
            .andExpect(jsonPath("$.prixFcfa").value(15000))
            .andExpect(jsonPath("$.quotaTotal").value(100))
            .andExpect(jsonPath("$.disponible").value(true));
    }

    /** Un autre organisateur reçoit exactement la réponse d'un événement inconnu : rien n'est révélé. */
    @Test
    void un_autre_organisateur_recoit_la_meme_reponse_qu_un_evenement_inconnu() throws Exception {
        Event event = evenement();

        mvc.perform(post("/api/events/{id}/categories", event.getId())
                .with(connecte(UUID.randomUUID(), "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(VIP))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Événement introuvable : " + event.getId()));
        UUID inconnu = UUID.randomUUID();
        mvc.perform(post("/api/events/{id}/categories", inconnu)
                .with(connecte(UUID.randomUUID(), "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(VIP))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Événement introuvable : " + inconnu));
    }

    @Test
    void creer_une_categorie_exige_d_etre_connecte_et_organisateur() throws Exception {
        Event event = evenement();

        mvc.perform(post("/api/events/{id}/categories", event.getId())
                .contentType(MediaType.APPLICATION_JSON).content(VIP))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/events/{id}/categories", event.getId())
                .with(connecte(organisateur, "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(VIP))
            .andExpect(status().isForbidden());
    }

    /** Bornes du DTO alignées sur V4 : 400 et non 500 (piège 15). */
    @Test
    void refuse_en_400_un_nom_trop_long_un_prix_negatif_ou_un_quota_nul() throws Exception {
        Event event = evenement();
        String nomTropLong = "x".repeat(256);

        for (String corps : new String[]{
                "{\"nom\":\"" + nomTropLong + "\",\"prixFcfa\":1,\"quotaTotal\":1}",
                "{\"nom\":\"VIP\",\"prixFcfa\":-1,\"quotaTotal\":1}",
                "{\"nom\":\"VIP\",\"prixFcfa\":1,\"quotaTotal\":0}",
                "{\"nom\":\"\",\"prixFcfa\":1,\"quotaTotal\":1}"}) {
            mvc.perform(post("/api/events/{id}/categories", event.getId())
                    .with(connecte(organisateur, "ORGANISATEUR"))
                    .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void refuse_en_409_une_categorie_pour_un_evenement_annule() throws Exception {
        Event event = evenement();
        eventService.annuler(event.getId(), organisateur);

        mvc.perform(post("/api/events/{id}/categories", event.getId())
                .with(connecte(organisateur, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(VIP))
            .andExpect(status().isConflict());
    }

    /** Un acheteur voit le prix et la disponibilité, jamais le stock. */
    @Test
    void un_visiteur_anonyme_voit_les_categories_d_un_evenement_publie_sans_le_stock() throws Exception {
        Event event = evenement();
        eventService.publier(event.getId(), organisateur);
        categoriePour(event);

        mvc.perform(get("/api/events/{id}/categories", event.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nom").value("VIP"))
            .andExpect(jsonPath("$[0].disponible").value(true))
            .andExpect(jsonPath("$[0].quotaTotal").doesNotExist())
            .andExpect(jsonPath("$[0].quotaVendu").doesNotExist());
    }

    /** Brouillon : 404 pour un visiteur (même message qu'un inconnu), quotas visibles pour l'organisateur. */
    @Test
    void les_categories_d_un_brouillon_ne_sont_visibles_que_de_son_organisateur() throws Exception {
        Event event = evenement();
        categoriePour(event);

        mvc.perform(get("/api/events/{id}/categories", event.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Événement introuvable : " + event.getId()));
        mvc.perform(get("/api/events/{id}/categories", event.getId()).with(connecte(organisateur, "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].quotaTotal").value(100))
            .andExpect(jsonPath("$[0].quotaVendu").value(0));
    }

    // ---- HTTP : comptes AGENT et affectation (feature/backend-auth-agent-accounts) ----

    /** Organisateur réellement inscrit : la création d'un agent vérifie son rôle en base. */
    private UUID organisateurInscrit() {
        return authService.registerOrganisateur(
            "orga-" + UUID.randomUUID() + "@billetplus.sn", "motdepasse", "Orga").getId();
    }

    private UUID agentCreePar(UUID organisateurId) throws Exception {
        String reponse = mvc.perform(post("/api/agents")
                .with(connecte(organisateurId, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"agent-" + UUID.randomUUID() + "@billetplus.sn\",\"motDePasse\":\"motdepasse\",\"nomComplet\":\"Agent\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.motDePasseHash").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(reponse, "$.id"));
    }

    @Test
    void un_organisateur_cree_un_agent_qui_peut_ensuite_se_connecter() throws Exception {
        UUID orga = organisateurInscrit();
        String email = "agent-" + UUID.randomUUID() + "@billetplus.sn";

        mvc.perform(post("/api/agents").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"motdepasse\",\"nomComplet\":\"Agent\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"motdepasse\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email)).isEqualTo("AGENT");
    }

    /** Un champ « role » envoyé est ignoré : le rôle est fixé par le serveur. */
    @Test
    void un_role_envoye_dans_le_corps_est_ignore() throws Exception {
        UUID orga = organisateurInscrit();
        String email = "agent-" + UUID.randomUUID() + "@billetplus.sn";

        mvc.perform(post("/api/agents").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"motdepasse\",\"nomComplet\":\"X\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email)).isEqualTo("AGENT");
    }

    @Test
    void seul_un_organisateur_cree_des_agents_et_un_mot_de_passe_court_est_refuse() throws Exception {
        String corps = "{\"email\":\"x-" + UUID.randomUUID() + "@billetplus.sn\",\"motDePasse\":\"motdepasse\",\"nomComplet\":\"X\"}";
        mvc.perform(post("/api/agents").contentType(MediaType.APPLICATION_JSON).content(corps))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/agents").with(connecte(UUID.randomUUID(), "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(corps))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/agents").with(connecte(organisateurInscrit(), "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"y@billetplus.sn\",\"motDePasse\":\"court\",\"nomComplet\":\"Y\"}"))
            .andExpect(status().isBadRequest());
    }

    /** Affecter puis retirer, chaque fois idempotent ; le port public suit (route de scan, branche suivante). */
    @Test
    void l_organisateur_affecte_et_retire_un_de_ses_agents() throws Exception {
        UUID orga = organisateurInscrit();
        UUID agent = agentCreePar(orga);
        UUID venueId = venueService.create("Grand Theatre", "Dakar", null, null).getId();
        Event event = eventService.create("Concert", null, Instant.now().plus(30, ChronoUnit.DAYS), null, venueId, orga);

        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/events/{id}/agents/{agentId}", event.getId(), agent).with(connecte(orga, "ORGANISATEUR")))
                .andExpect(status().isNoContent());
        }
        assertThat(statutEvenements.estAgentAffecte(event.getId(), agent)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_agents WHERE event_id = ?", Integer.class, event.getId())).isEqualTo(1);

        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/events/{id}/agents/{agentId}", event.getId(), agent).with(connecte(orga, "ORGANISATEUR")))
                .andExpect(status().isNoContent());
        }
        assertThat(statutEvenements.estAgentAffecte(event.getId(), agent)).isFalse();
    }

    @Test
    void on_ne_peut_affecter_ni_l_agent_d_un_autre_ni_a_l_evenement_d_un_autre() throws Exception {
        UUID orga = organisateurInscrit();
        UUID autreOrga = organisateurInscrit();
        UUID agentDeLAutre = agentCreePar(autreOrga);
        UUID venueId = venueService.create("Grand Theatre", "Dakar", null, null).getId();
        Event event = eventService.create("Concert", null, Instant.now().plus(30, ChronoUnit.DAYS), null, venueId, orga);

        mvc.perform(put("/api/events/{id}/agents/{agentId}", event.getId(), agentDeLAutre).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Agent introuvable : " + agentDeLAutre));
        mvc.perform(put("/api/events/{id}/agents/{agentId}", event.getId(), agentDeLAutre).with(connecte(autreOrga, "ORGANISATEUR")))
            .andExpect(status().isForbidden());
        assertThat(statutEvenements.estAgentAffecte(event.getId(), agentDeLAutre)).isFalse();
    }
}
