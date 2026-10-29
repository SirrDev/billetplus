package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.usecase.IssueTicketUseCase;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;
import com.billetplus.tickets.domain.exception.TicketAlreadyScannedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests d'intégration des adaptateurs JPA du module tickets, sur un vrai
 * PostgreSQL (Testcontainers) avec les migrations Flyway réelles.
 *
 * Image PostGIS obligatoire : V1__enable_extensions.sql crée l'extension postgis,
 * absente de l'image postgres standard.
 *
 * Pas de transaction automatique par test (NOT_SUPPORTED) : vérifier le
 * verrouillage optimiste exige deux transactions réelles qui se chevauchent,
 * ouvertes explicitement avec TransactionTemplate. Les save() des adaptateurs
 * exigent d'ailleurs une transaction de l'appelant (propagation MANDATORY).
 * Les tables sont vidées avant chaque test.
 *
 * Ignorés (et non en échec) si Docker n'est pas disponible.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaTicketCategoryRepositoryAdapter.class, JpaTicketRepositoryAdapter.class, IssueTicketUseCase.class})
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
    private IssueTicketUseCase issueTicket;
    /** La vraie signature Ed25519 a ses propres tests ; ici seule la transaction compte. */
    @MockBean
    private QRSigner qrSigner;
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

    private TicketCategory enregistrer(TicketCategory category) {
        return tx.execute(s -> categories.save(category));
    }

    private Ticket enregistrer(Ticket ticket) {
        return tx.execute(s -> tickets.save(ticket));
    }

    private TicketCategory categorieEnregistree(int quotaTotal) {
        return enregistrer(new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), quotaTotal));
    }

    private Ticket lireBillet(UUID id) {
        return tx.execute(s -> TicketMapper.versDomaine(ticketsSpringData.findById(id).orElseThrow()));
    }

    private long versionEnBase(String table, UUID id) {
        return jdbc.queryForObject("SELECT version FROM " + table + " WHERE id = ?", Long.class, id);
    }

    private int quotaVenduEnBase(UUID categoryId) {
        return jdbc.queryForObject("SELECT quota_vendu FROM ticket_categories WHERE id = ?", Integer.class, categoryId);
    }

    private int nombreDeBillets() {
        return jdbc.queryForObject("SELECT count(*) FROM tickets", Integer.class);
    }

    // ---- Catégories ----

    @Test
    void une_categorie_enregistree_se_relit_a_l_identique_avec_la_version_initiale() {
        TicketCategory origine = new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), 100);
        origine.reserver(4);

        enregistrer(origine);

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

        assertThat(quotaVenduEnBase(category.getId())).isEqualTo(5);
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
        assertThat(quotaVenduEnBase(category.getId())).isEqualTo(2);
        assertThat(versionEnBase("ticket_categories", category.getId())).isEqualTo(1);
    }

    /**
     * Sans transaction de l'appelant, save() relirait la version la plus
     * récente et le contrôle de version ne protégerait plus rien : l'appel
     * doit échouer immédiatement, sans rien écrire.
     */
    @Test
    void refuse_d_enregistrer_une_categorie_hors_transaction() {
        TicketCategory category = new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), 10);

        assertThatThrownBy(() -> categories.save(category))
            .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(categories.findById(category.getId())).isEmpty();
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

        enregistrer(origine);

        assertThat(lireBillet(origine.getId())).usingRecursiveComparison().isEqualTo(origine);
        assertThat(versionEnBase("tickets", origine.getId())).isZero();
    }

    @Test
    void un_scan_persiste_le_statut_et_la_date_de_scan() {
        TicketCategory category = categorieEnregistree(10);
        Ticket ticket = enregistrer(new Ticket(category.getEventId(), category.getId(), UUID.randomUUID()));

        tx.executeWithoutResult(s -> {
            Ticket lu = TicketMapper.versDomaine(ticketsSpringData.findById(ticket.getId()).orElseThrow());
            lu.marquerCommeScanne();
            tickets.save(lu);
        });

        Ticket relu = lireBillet(ticket.getId());
        assertThat(relu.getStatus()).isEqualTo(TicketStatus.SCANNE);
        assertThat(relu.getScanneLe()).isNotNull();
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
        Ticket ticket = enregistrer(new Ticket(category.getEventId(), category.getId(), UUID.randomUUID()));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            Ticket luParA = TicketMapper.versDomaine(ticketsSpringData.findById(ticket.getId()).orElseThrow());

            nouvelleTx.executeWithoutResult(s2 -> {
                Ticket luParB = TicketMapper.versDomaine(ticketsSpringData.findById(ticket.getId()).orElseThrow());
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
    void refuse_d_enregistrer_un_billet_hors_transaction() {
        TicketCategory category = categorieEnregistree(10);
        Ticket ticket = new Ticket(category.getEventId(), category.getId(), UUID.randomUUID());

        assertThatThrownBy(() -> tickets.save(ticket))
            .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(nombreDeBillets()).isZero();
    }

    @Test
    void detecte_les_billets_deja_emis_pour_une_commande_et_une_categorie() {
        TicketCategory category = categorieEnregistree(10);
        TicketCategory autreCategorie = categorieEnregistree(10);
        UUID orderId = UUID.randomUUID();
        enregistrer(new Ticket(category.getEventId(), category.getId(), orderId));

        assertThat(tickets.existsByOrderIdAndCategoryId(orderId, category.getId())).isTrue();
        assertThat(tickets.existsByOrderIdAndCategoryId(orderId, autreCategorie.getId())).isFalse();
        assertThat(tickets.existsByOrderIdAndCategoryId(UUID.randomUUID(), category.getId())).isFalse();
    }

    @Test
    void refuse_un_billet_rattache_a_une_categorie_inexistante() {
        Ticket orphelin = new Ticket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> enregistrer(orphelin))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- Cas d'usage IssueTicketUseCase, dans sa vraie transaction ----

    @Test
    void l_emission_persiste_les_billets_et_le_quota_puis_refuse_un_rejeu_de_la_commande() {
        TicketCategory category = categorieEnregistree(10);
        UUID orderId = UUID.randomUUID();
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn("payload.signature");

        issueTicket.execute(category.getId(), orderId, 3);

        assertThat(nombreDeBillets()).isEqualTo(3);
        assertThat(quotaVenduEnBase(category.getId())).isEqualTo(3);
        assertThatThrownBy(() -> issueTicket.execute(category.getId(), orderId, 3))
            .isInstanceOf(BilletsDejaEmisException.class);
        assertThat(nombreDeBillets()).isEqualTo(3);
        assertThat(quotaVenduEnBase(category.getId())).isEqualTo(3);
    }

    /**
     * Une panne de signature après la réservation et l'écriture des premiers
     * billets doit tout annuler : ni quota consommé, ni billet sans QR valide.
     */
    @Test
    void une_panne_de_signature_annule_toute_l_emission() {
        TicketCategory category = categorieEnregistree(10);
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class)))
            .thenThrow(new IllegalStateException("Panne de signature simulee"));

        assertThatThrownBy(() -> issueTicket.execute(category.getId(), UUID.randomUUID(), 3))
            .isInstanceOf(IllegalStateException.class);

        assertThat(nombreDeBillets()).isZero();
        assertThat(quotaVenduEnBase(category.getId())).isZero();
    }
}
