package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.shared.Money;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import com.billetplus.tickets.domain.exception.TicketAlreadyScannedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration des adaptateurs JPA du module tickets, sur un vrai
 * PostgreSQL (Testcontainers) avec les migrations Flyway réelles.
 *
 * Image PostGIS obligatoire : V1__enable_extensions.sql crée l'extension postgis,
 * absente de l'image postgres standard.
 *
 * Pas de transaction automatique par test (NOT_SUPPORTED) : vérifier le
 * verrouillage optimiste exige deux transactions réelles qui se chevauchent,
 * ouvertes explicitement avec TransactionTemplate. Les tables sont vidées
 * avant chaque test.
 *
 * Ignorés (et non en échec) si Docker n'est pas disponible.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaTicketCategoryRepositoryAdapter.class, JpaTicketRepositoryAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class TicketPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JpaTicketCategoryRepositoryAdapter categories;
    @Autowired
    private JpaTicketRepositoryAdapter tickets;
    /** Lecture directe des billets : le port TicketRepository n'expose pas encore de findById. */
    @Autowired
    private SpringDataTicketRepository ticketsSpringData;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private TransactionTemplate nouvelleTx;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM ticket_categories");
        tx = new TransactionTemplate(transactionManager);
        nouvelleTx = new TransactionTemplate(transactionManager);
        nouvelleTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private TicketCategory categorieEnregistree(int quotaTotal) {
        return categories.save(new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), quotaTotal));
    }

    private long versionEnBase(String table, UUID id) {
        return jdbc.queryForObject("SELECT version FROM " + table + " WHERE id = ?", Long.class, id);
    }

    // ---- Catégories ----

    @Test
    void une_categorie_enregistree_se_relit_a_l_identique_avec_la_version_initiale() {
        TicketCategory origine = new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), 100);
        origine.reserver(4);

        categories.save(origine);

        assertThat(categories.findById(origine.getId())).get()
            .usingRecursiveComparison().isEqualTo(origine);
        assertThat(versionEnBase("ticket_categories", origine.getId())).isZero();
    }

    @Test
    void une_mise_a_jour_persiste_le_quota_vendu_et_incremente_la_version() {
        TicketCategory category = categorieEnregistree(100);

        tx.executeWithoutResult(s -> {
            TicketCategory lue = categories.findById(category.getId()).orElseThrow();
            lue.reserver(5);
            categories.save(lue);
        });

        assertThat(categories.findById(category.getId()).orElseThrow().getQuotaVendu()).isEqualTo(5);
        assertThat(versionEnBase("ticket_categories", category.getId())).isEqualTo(1);
    }

    /**
     * Le scénario de survente : A et B lisent la même catégorie, B réserve et
     * valide en premier. La réservation de A, calculée sur un état périmé, doit
     * être rejetée au lieu d'écraser celle de B.
     */
    @Test
    void refuse_une_reservation_calculee_sur_une_version_perimee() {
        TicketCategory category = categorieEnregistree(10);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            TicketCategory lueParA = categories.findById(category.getId()).orElseThrow();

            nouvelleTx.executeWithoutResult(s2 -> {
                TicketCategory lueParB = categories.findById(category.getId()).orElseThrow();
                lueParB.reserver(2);
                categories.save(lueParB);
            });

            lueParA.reserver(3);
            categories.save(lueParA);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // Seule la réservation de B a été validée : ni 3 (écrasement), ni 5.
        assertThat(categories.findById(category.getId()).orElseThrow().getQuotaVendu()).isEqualTo(2);
        assertThat(versionEnBase("ticket_categories", category.getId())).isEqualTo(1);
    }

    @Test
    void une_categorie_inconnue_n_est_pas_trouvee() {
        assertThat(categories.findById(UUID.randomUUID())).isEmpty();
    }

    // ---- Billets ----

    @Test
    void un_billet_enregistre_se_relit_a_l_identique() {
        TicketCategory category = categorieEnregistree(10);
        Ticket origine = new Ticket(category.getEventId(), category.getId(), UUID.randomUUID());

        tickets.save(origine);

        Ticket relu = tx.execute(s -> TicketMapper.versDomaine(
            ticketsSpringData.findById(origine.getId()).orElseThrow()));
        assertThat(relu).usingRecursiveComparison().isEqualTo(origine);
        assertThat(versionEnBase("tickets", origine.getId())).isZero();
    }

    @Test
    void un_scan_persiste_le_statut_et_la_date_de_scan() {
        TicketCategory category = categorieEnregistree(10);
        Ticket ticket = tickets.save(new Ticket(category.getEventId(), category.getId(), UUID.randomUUID()));

        ticket.marquerCommeScanne();
        tickets.save(ticket);

        TicketJpaEntity enBase = ticketsSpringData.findById(ticket.getId()).orElseThrow();
        assertThat(enBase.getStatus()).isEqualTo(TicketStatus.SCANNE);
        assertThat(enBase.getScanneLe()).isNotNull();
        assertThat(versionEnBase("tickets", ticket.getId())).isEqualTo(1);
    }

    /**
     * Deux scanners valident le même billet au même instant : chacun le lit
     * EMIS, donc la règle du domaine (TicketAlreadyScannedException) ne voit
     * rien. C'est la version qui doit rejeter le second.
     */
    @Test
    void refuse_un_second_scan_concurrent_du_meme_billet() {
        TicketCategory category = categorieEnregistree(10);
        Ticket ticket = tickets.save(new Ticket(category.getEventId(), category.getId(), UUID.randomUUID()));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            Ticket luParA = TicketMapper.versDomaine(
                ticketsSpringData.findById(ticket.getId()).orElseThrow());

            nouvelleTx.executeWithoutResult(s2 -> {
                Ticket luParB = TicketMapper.versDomaine(
                    ticketsSpringData.findById(ticket.getId()).orElseThrow());
                luParB.marquerCommeScanne();
                tickets.save(luParB);
            });

            luParA.marquerCommeScanne();  // ne lève pas : A a lu le billet EMIS
            tickets.save(luParA);
        }))
            .isInstanceOf(OptimisticLockingFailureException.class)
            .isNotInstanceOf(TicketAlreadyScannedException.class);

        assertThat(versionEnBase("tickets", ticket.getId())).isEqualTo(1);
    }

    @Test
    void detecte_les_billets_deja_emis_pour_une_commande_et_une_categorie() {
        TicketCategory category = categorieEnregistree(10);
        TicketCategory autreCategorie = categorieEnregistree(10);
        UUID orderId = UUID.randomUUID();
        tickets.save(new Ticket(category.getEventId(), category.getId(), orderId));

        assertThat(tickets.existsByOrderIdAndCategoryId(orderId, category.getId())).isTrue();
        assertThat(tickets.existsByOrderIdAndCategoryId(orderId, autreCategorie.getId())).isFalse();
        assertThat(tickets.existsByOrderIdAndCategoryId(UUID.randomUUID(), category.getId())).isFalse();
    }

    @Test
    void refuse_un_billet_rattache_a_une_categorie_inexistante() {
        Ticket orphelin = new Ticket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> tickets.save(orphelin))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
