package com.billetplus;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

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
    private VenueService venueService;
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
}
