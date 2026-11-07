package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.EventStatus;
import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.usecase.CancelEventTicketsUseCase;
import com.billetplus.tickets.application.usecase.IssueTicketUseCase;
import com.billetplus.tickets.application.usecase.ReleaseSeatsUseCase;
import com.billetplus.tickets.application.usecase.ReserveSeatsUseCase;
import com.billetplus.tickets.application.usecase.ResultatScan;
import com.billetplus.tickets.application.usecase.ScanTicketUseCase;
import com.billetplus.tickets.application.usecase.ValidateTicketScanUseCase;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.VenteFermeeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
class ContexteCompletIntegrationTest extends ContexteCompletTestBase {


    @Autowired
    private ApplicationContext contexte;
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
    private ScanTicketUseCase scanTicket;
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
        UUID venueId = venueService.create("Grand Theatre", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        return eventService.create("Concert", null, Instant.now().plus(30, ChronoUnit.DAYS), null, venueId, organisateur, null);
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

        organisateurVerifie(organisateur);

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
        organisateurVerifie(organisateur);
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
        organisateurVerifie(organisateur);
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
        organisateurVerifie(organisateur);
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
        UUID venueId = venueService.create("Grand Theatre", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        Event event = eventService.create("Concert", null, Instant.now().plus(30, ChronoUnit.DAYS), null, venueId, orga, null);

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
        UUID venueId = venueService.create("Grand Theatre", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        Event event = eventService.create("Concert", null, Instant.now().plus(30, ChronoUnit.DAYS), null, venueId, orga, null);

        mvc.perform(put("/api/events/{id}/agents/{agentId}", event.getId(), agentDeLAutre).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Agent introuvable : " + agentDeLAutre));
        // Brouillon d'un autre : 404, indiscernable d'un événement inconnu ; une fois publié : 403.
        mvc.perform(put("/api/events/{id}/agents/{agentId}", event.getId(), agentDeLAutre).with(connecte(autreOrga, "ORGANISATEUR")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Événement introuvable : " + event.getId()));
        organisateurVerifie(orga);
        eventService.publier(event.getId(), orga);
        mvc.perform(put("/api/events/{id}/agents/{agentId}", event.getId(), agentDeLAutre).with(connecte(autreOrga, "ORGANISATEUR")))
            .andExpect(status().isForbidden());
        assertThat(statutEvenements.estAgentAffecte(event.getId(), agentDeLAutre)).isFalse();
    }

    // ---- HTTP : route de scan (feature/backend-tickets-scan-route) ----

    /** Un événement publié, avec un agent affecté et un billet émis dont le QR « vérifié » est QR_VALIDE. */
    private record Controle(UUID organisateur, UUID agent, Event event, UUID ticketId) {}

    private static final String QR_VALIDE = "qr-valide";

    private Controle controlePret() throws Exception {
        UUID orga = organisateurInscrit();
        UUID agent = agentCreePar(orga);
        UUID venueId = venueService.create("Grand Theatre", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        Event event = eventService.create("Concert", null, Instant.now().plus(30, ChronoUnit.DAYS), null, venueId, orga, null);
        organisateurVerifie(orga);
        eventService.publier(event.getId(), orga);
        mvc.perform(put("/api/events/{id}/agents/{agentId}", event.getId(), agent).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isNoContent());
        TicketCategory category = categoriePour(event);
        var billet = issueTicket.execute(category.getId(), UUID.randomUUID(), 1).get(0).ticket();
        when(qrSigner.verify(QR_VALIDE)).thenReturn(com.billetplus.tickets.application.port.QrVerificationResult.valide(
            billet.getId(), billet.getEventId(), billet.getEmisLe().truncatedTo(ChronoUnit.MILLIS)));
        return new Controle(orga, agent, event, billet.getId());
    }

    private static String scan(String qr) {
        return "{\"qrContent\":\"" + qr + "\"}";
    }

    @Test
    void un_agent_affecte_fait_entrer_un_billet_puis_le_signale_deja_scanne() throws Exception {
        Controle c = controlePret();

        mvc.perform(post("/api/events/{id}/scans", c.event().getId()).with(connecte(c.agent(), "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(scan(QR_VALIDE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue").value("ACCEPTE"))
            .andExpect(jsonPath("$.ticketId").value(c.ticketId().toString()));
        mvc.perform(post("/api/events/{id}/scans", c.event().getId()).with(connecte(c.agent(), "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(scan(QR_VALIDE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue").value("DEJA_SCANNE"))
            .andExpect(jsonPath("$.premierScanLe").exists());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, c.ticketId())).isEqualTo("SCANNE");
    }

    /** INVALIDE ne porte que son nom : aucun identifiant, aucun indice pour un faussaire. */
    @Test
    void un_qr_invalide_repond_200_invalide_sans_aucune_donnee() throws Exception {
        Controle c = controlePret();
        when(qrSigner.verify("qr-falsifie")).thenReturn(com.billetplus.tickets.application.port.QrVerificationResult.invalide());

        mvc.perform(post("/api/events/{id}/scans", c.event().getId()).with(connecte(c.agent(), "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(scan("qr-falsifie")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue").value("INVALIDE"))
            .andExpect(jsonPath("$.ticketId").doesNotExist())
            .andExpect(jsonPath("$.premierScanLe").doesNotExist())
            .andExpect(jsonPath("$.statut").doesNotExist());
    }

    /** Un agent non affecté (d'un autre organisateur, ou du même mais pas à cet événement) : 403, billet intact. */
    @Test
    void un_agent_non_affecte_a_l_evenement_recoit_403_et_le_billet_reste_valide() throws Exception {
        Controle c = controlePret();
        UUID agentNonAffecte = agentCreePar(c.organisateur());

        mvc.perform(post("/api/events/{id}/scans", c.event().getId()).with(connecte(agentNonAffecte, "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(scan(QR_VALIDE)))
            .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, c.ticketId())).isEqualTo("EMIS");
    }

    /** L'organisateur peut contrôler l'entrée de son propre événement ; un autre organisateur non. */
    @Test
    void l_organisateur_scanne_son_evenement_mais_pas_celui_d_un_autre() throws Exception {
        Controle c = controlePret();

        mvc.perform(post("/api/events/{id}/scans", c.event().getId()).with(connecte(organisateurInscrit(), "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(scan(QR_VALIDE)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/events/{id}/scans", c.event().getId()).with(connecte(c.organisateur(), "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(scan(QR_VALIDE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue").value("ACCEPTE"));
    }

    /** Un agent affecté à l'événement B ne fait pas entrer, à B, un billet de A : AUTRE_EVENEMENT, billet intact. */
    @Test
    void un_billet_d_un_autre_evenement_est_signale_sans_etre_consomme() throws Exception {
        Controle a = controlePret();
        UUID venueId = venueService.create("Stade", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.DAKAR).getId();
        Event b = eventService.create("Match", null, Instant.now().plus(40, ChronoUnit.DAYS), null, venueId, a.organisateur(), null);
        organisateurVerifie(a.organisateur());
        eventService.publier(b.getId(), a.organisateur());
        mvc.perform(put("/api/events/{id}/agents/{agentId}", b.getId(), a.agent()).with(connecte(a.organisateur(), "ORGANISATEUR")))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/events/{id}/scans", b.getId()).with(connecte(a.agent(), "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(scan(QR_VALIDE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue").value("AUTRE_EVENEMENT"));
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, a.ticketId())).isEqualTo("EMIS");
    }

    @Test
    void scanner_exige_d_etre_connecte_et_borne_la_taille_du_qr() throws Exception {
        Controle c = controlePret();

        mvc.perform(post("/api/events/{id}/scans", c.event().getId())
                .contentType(MediaType.APPLICATION_JSON).content(scan(QR_VALIDE)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/events/{id}/scans", c.event().getId()).with(connecte(c.agent(), "AGENT"))
                .contentType(MediaType.APPLICATION_JSON).content(scan("x".repeat(1025))))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, c.ticketId())).isEqualTo("EMIS");
    }

    /**
     * 8 agents scannent le même billet au même instant, par le vrai chemin
     * (ScanTicketUseCase, transactions réelles) : grâce au rejeu d'un conflit de
     * version, exactement un ACCEPTE, sept DEJA_SCANNE, et AUCUNE erreur. Le
     * rejeu ne peut pas lui-même entrer en conflit : le chemin DEJA_SCANNE n'écrit rien.
     */
    @Test
    void huit_scans_simultanes_du_meme_billet_donnent_un_accepte_et_sept_deja_scanne_sans_erreur() throws Exception {
        Controle c = controlePret();
        int agents = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(agents);
        java.util.concurrent.CountDownLatch prets = new java.util.concurrent.CountDownLatch(agents);
        java.util.concurrent.CountDownLatch depart = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Object>> futurs = new java.util.ArrayList<>();
            for (int i = 0; i < agents; i++) {
                futurs.add(pool.submit(() -> {
                    prets.countDown();
                    depart.await();
                    try {
                        return scanTicket.execute(QR_VALIDE, c.event().getId(), c.agent());
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(prets.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            depart.countDown();
            List<Object> resultats = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<Object> futur : futurs) {
                resultats.add(futur.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }

            assertThat(resultats).allSatisfy(r -> assertThat(r).isInstanceOf(ResultatScan.class));
            assertThat(resultats).filteredOn(r -> ((ResultatScan) r).issue() == ResultatScan.Issue.ACCEPTE).hasSize(1);
            assertThat(resultats).filteredOn(r -> ((ResultatScan) r).issue() == ResultatScan.Issue.DEJA_SCANNE).hasSize(agents - 1);
            assertThat(jdbc.queryForObject("SELECT version FROM tickets WHERE id = ?", Long.class, c.ticketId())).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }
}
