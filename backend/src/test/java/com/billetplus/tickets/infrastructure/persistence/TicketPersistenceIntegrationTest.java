package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.QrVerificationResult;
import com.billetplus.tickets.application.port.SeatReservationStore;
import com.billetplus.tickets.application.usecase.IssueTicketUseCase;
import com.billetplus.tickets.application.usecase.ResultatScan;
import com.billetplus.tickets.application.usecase.ValidateTicketScanUseCase;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;
import com.billetplus.tickets.domain.exception.QuotaDepasseException;
import com.billetplus.tickets.domain.exception.TicketAlreadyScannedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
@Import({JpaTicketCategoryRepositoryAdapter.class, JpaTicketRepositoryAdapter.class,
         JpaTicketIssuanceRepositoryAdapter.class, IssueTicketUseCase.class, ValidateTicketScanUseCase.class})
// Absente de @DataJpaTest mais active dans l'application : sans elle, le test
// ne verrait pas la traduction d'exceptions que Spring applique aux beans
// @Repository (qui transformerait une BusinessConflictException en 500).
@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)
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
    @Autowired
    private JpaTicketIssuanceRepositoryAdapter emissions;
    @Autowired
    private IssueTicketUseCase issueTicket;
    @Autowired
    private ValidateTicketScanUseCase validateScan;
    /** La vraie signature Ed25519 a ses propres tests ; ici seule la transaction compte. */
    @MockBean
    private QRSigner qrSigner;
    /** Redis a ses propres tests (RedisSeatReservationStoreIntegrationTest) ; ici, seul le moment de la libération compte. */
    @MockBean
    private SeatReservationStore reservationStore;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private TransactionTemplate nouvelleTx;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM ticket_issuances");
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

    /** Par le port, hors transaction : findById n'en exige pas (contrairement à save). */
    private Ticket lireBillet(UUID id) {
        return tickets.findById(id).orElseThrow();
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

    private int nombreDEmissions() {
        return jdbc.queryForObject("SELECT count(*) FROM ticket_issuances", Integer.class);
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
    void un_billet_inconnu_n_est_pas_trouve() {
        assertThat(tickets.findById(UUID.randomUUID())).isEmpty();
    }

    /** Un billet scanné se relit avec son statut et sa date de scan : c'est ce que lira le contrôle d'entrée. */
    @Test
    void un_billet_scanne_se_relit_avec_son_statut_et_sa_date_de_scan() {
        TicketCategory category = categorieEnregistree(10);
        Ticket ticket = new Ticket(category.getEventId(), category.getId(), UUID.randomUUID());
        ticket.marquerCommeScanne();
        enregistrer(ticket);

        Ticket relu = lireBillet(ticket.getId());

        assertThat(relu.getStatus()).isEqualTo(TicketStatus.SCANNE);
        assertThat(relu.getScanneLe()).isEqualTo(ticket.getScanneLe());
    }

    @Test
    void un_scan_persiste_le_statut_et_la_date_de_scan() {
        TicketCategory category = categorieEnregistree(10);
        Ticket ticket = enregistrer(new Ticket(category.getEventId(), category.getId(), UUID.randomUUID()));

        tx.executeWithoutResult(s -> {
            Ticket lu = tickets.findById(ticket.getId()).orElseThrow();
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
            Ticket luParA = tickets.findById(ticket.getId()).orElseThrow();

            nouvelleTx.executeWithoutResult(s2 -> {
                Ticket luParB = tickets.findById(ticket.getId()).orElseThrow();
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

    // ---- Registre d'émission (V5) ----

    @Test
    void enregistre_une_emission_par_couple_commande_categorie() {
        TicketCategory category = categorieEnregistree(10);
        TicketCategory autreCategorie = categorieEnregistree(10);
        UUID orderId = UUID.randomUUID();

        tx.executeWithoutResult(s -> {
            emissions.enregistrer(orderId, category.getId(), 3);
            emissions.enregistrer(orderId, autreCategorie.getId(), 1);       // même commande, autre catégorie
            emissions.enregistrer(UUID.randomUUID(), category.getId(), 2);   // autre commande, même catégorie
        });

        assertThat(nombreDEmissions()).isEqualTo(3);
    }

    /**
     * Le cas du webhook rejoué : une émission déjà validée par une transaction
     * précédente. Avec save() de Spring Data (merge), ce doublon serait devenu
     * un UPDATE silencieux ; l'INSERT ... ON CONFLICT DO NOTHING n'insère rien
     * et l'adaptateur le signale. La quantité d'origine n'est pas écrasée.
     */
    @Test
    void refuse_une_seconde_emission_deja_validee_pour_le_meme_couple() {
        TicketCategory category = categorieEnregistree(10);
        UUID orderId = UUID.randomUUID();
        tx.executeWithoutResult(s -> emissions.enregistrer(orderId, category.getId(), 3));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> emissions.enregistrer(orderId, category.getId(), 5)))
            .isInstanceOf(BilletsDejaEmisException.class);

        assertThat(nombreDEmissions()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantite FROM ticket_issuances", Integer.class)).isEqualTo(3);
    }

    /**
     * Deux appels pour le même couple dans UNE transaction (une commande dont
     * deux lignes portent la même catégorie) : 409 et non 500. Avec persist,
     * Hibernate levait EntityExistsException. La transaction PostgreSQL reste
     * de plus utilisable après le refus : aucune erreur SQL ne l'a interrompue.
     */
    @Test
    void refuse_un_doublon_dans_la_meme_transaction_sans_interrompre_la_transaction() {
        TicketCategory category = categorieEnregistree(10);
        UUID orderId = UUID.randomUUID();

        Integer lignesVuesApresRefus = tx.execute(s -> {
            emissions.enregistrer(orderId, category.getId(), 2);
            assertThatThrownBy(() -> emissions.enregistrer(orderId, category.getId(), 2))
                .isInstanceOf(BilletsDejaEmisException.class);
            // Une transaction PostgreSQL interrompue refuserait cette requête (25P02).
            Integer lignes = jdbc.queryForObject("SELECT count(*) FROM ticket_issuances", Integer.class);
            s.setRollbackOnly();
            return lignes;
        });

        assertThat(lignesVuesApresRefus).isEqualTo(1);
        assertThat(nombreDEmissions()).isZero();  // annulée par setRollbackOnly
    }

    @Test
    void refuse_d_enregistrer_une_emission_hors_transaction() {
        TicketCategory category = categorieEnregistree(10);

        assertThatThrownBy(() -> emissions.enregistrer(UUID.randomUUID(), category.getId(), 1))
            .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(nombreDEmissions()).isZero();
    }

    /** Une autre violation de contrainte (ici la FK) n'est PAS déguisée en doublon. */
    @Test
    void ne_traduit_en_doublon_que_la_violation_de_cle_primaire() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                emissions.enregistrer(UUID.randomUUID(), UUID.randomUUID(), 1)))
            .isNotInstanceOf(BilletsDejaEmisException.class);

        assertThat(nombreDEmissions()).isZero();
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
        assertThat(nombreDEmissions()).isEqualTo(1);
        assertThatThrownBy(() -> issueTicket.execute(category.getId(), orderId, 3))
            .isInstanceOf(BilletsDejaEmisException.class);
        assertThat(nombreDeBillets()).isEqualTo(3);
        assertThat(quotaVenduEnBase(category.getId())).isEqualTo(3);
        assertThat(nombreDEmissions()).isEqualTo(1);
        // Libérée une seule fois : après la vente validée, pas après le rejeu refusé.
        verify(reservationStore, times(1)).liberer(category.getId(), orderId);
    }

    /**
     * L'émission est enregistrée avant la réservation : un quota insuffisant
     * doit annuler aussi la ligne du registre, sinon la commande ne pourrait
     * plus jamais être émise (même après une libération de places).
     */
    @Test
    void un_quota_insuffisant_annule_aussi_l_enregistrement_de_l_emission() {
        TicketCategory category = categorieEnregistree(2);
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> issueTicket.execute(category.getId(), orderId, 3))
            .isInstanceOf(QuotaDepasseException.class);

        assertThat(nombreDEmissions()).isZero();
        assertThat(nombreDeBillets()).isZero();
        assertThat(quotaVenduEnBase(category.getId())).isZero();
        // Émission annulée : l'acheteur garde sa réservation pour une nouvelle tentative.
        verifyNoInteractions(reservationStore);
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
        assertThat(nombreDEmissions()).isZero();
    }

    // ---- Concurrence réelle : N threads, N transactions, départ simultané ----

    private static final int THREADS = 8;

    /**
     * Lance {@code n} appels en parallèle, libérés au même instant par une
     * barrière, et renvoie le résultat de chacun : la valeur rendue, ou
     * l'exception levée. Chaque appel à issueTicket passe par le proxy
     * transactionnel : une transaction réelle et une connexion par thread.
     */
    private List<Object> enParallele(int n, IntFunction<Callable<?>> tache) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch prets = new CountDownLatch(n);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<Object>> futurs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<?> appel = tache.apply(i);
                futurs.add(pool.submit(() -> {
                    prets.countDown();
                    depart.await();
                    try {
                        return appel.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(prets.await(10, TimeUnit.SECONDS)).isTrue();
            depart.countDown();
            List<Object> resultats = new ArrayList<>();
            for (Future<Object> futur : futurs) {
                resultats.add(futur.get(60, TimeUnit.SECONDS));
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long succes(List<Object> resultats) {
        return resultats.stream().filter(r -> !(r instanceof Throwable)).count();
    }

    /**
     * Le webhook Wave reçu plusieurs fois au même instant : une seule émission
     * passe. Les autres attendent sur la clé primaire de ticket_issuances, puis
     * échouent en 409 une fois la première validée.
     */
    @Test
    void des_confirmations_simultanees_de_la_meme_commande_n_emettent_qu_une_fois() throws Exception {
        TicketCategory category = categorieEnregistree(100);
        UUID orderId = UUID.randomUUID();
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn("payload.signature");

        List<Object> resultats = enParallele(THREADS, i -> () -> issueTicket.execute(category.getId(), orderId, 3));

        assertThat(succes(resultats)).isEqualTo(1);
        assertThat(resultats).filteredOn(r -> r instanceof Throwable)
            .hasSize(THREADS - 1)
            .allSatisfy(r -> assertThat(r).isInstanceOf(BilletsDejaEmisException.class));
        assertThat(nombreDeBillets()).isEqualTo(3);
        assertThat(quotaVenduEnBase(category.getId())).isEqualTo(3);
        assertThat(nombreDEmissions()).isEqualTo(1);
    }

    /**
     * Des commandes différentes sur la même catégorie, quota suffisant pour
     * toutes : chaque perdant d'une course reçoit un conflit de version (409,
     * à relancer par l'appelant), jamais une réservation écrasée. Le quota
     * vendu est exactement la somme des émissions réussies.
     */
    @Test
    void des_commandes_simultanees_ne_perdent_aucune_reservation() throws Exception {
        TicketCategory category = categorieEnregistree(100);
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn("payload.signature");

        List<Object> resultats = enParallele(THREADS, i -> () -> issueTicket.execute(category.getId(), UUID.randomUUID(), 3));

        long reussies = succes(resultats);
        assertThat(reussies).isGreaterThanOrEqualTo(1);
        assertThat(resultats).filteredOn(r -> r instanceof Throwable)
            .allSatisfy(r -> assertThat(r).isInstanceOf(OptimisticLockingFailureException.class));
        assertThat(quotaVenduEnBase(category.getId())).isEqualTo(3 * reussies);
        assertThat(nombreDeBillets()).isEqualTo(3 * reussies);
        assertThat(nombreDEmissions()).isEqualTo(reussies);  // les perdants n'ont rien laissé
    }

    /**
     * Le pic de mise en vente : plus de demandes que de places. Le quota n'est
     * jamais dépassé ; chaque refus est soit un quota insuffisant (lu après la
     * validation d'un concurrent), soit un conflit de version (lu avant).
     */
    @Test
    void des_commandes_simultanees_ne_depassent_jamais_le_quota() throws Exception {
        TicketCategory category = categorieEnregistree(10);  // place pour 3 lots de 3, pas pour 8
        when(qrSigner.sign(any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn("payload.signature");

        List<Object> resultats = enParallele(THREADS, i -> () -> issueTicket.execute(category.getId(), UUID.randomUUID(), 3));

        long reussies = succes(resultats);
        assertThat(reussies).isBetween(1L, 3L);
        assertThat(resultats).filteredOn(r -> r instanceof Throwable)
            .allSatisfy(r -> assertThat(r).isInstanceOfAny(
                QuotaDepasseException.class, OptimisticLockingFailureException.class));
        assertThat(quotaVenduEnBase(category.getId())).isEqualTo(3 * reussies).isLessThanOrEqualTo(10);
        assertThat(nombreDeBillets()).isEqualTo(3 * reussies);
        assertThat(nombreDEmissions()).isEqualTo(reussies);
    }

    // ---- Contrôle d'entrée (ValidateTicketScanUseCase), dans sa vraie transaction ----

    @Test
    void un_scan_valide_persiste_le_billet_scanne() {
        TicketCategory category = categorieEnregistree(10);
        Ticket ticket = enregistrer(new Ticket(category.getEventId(), category.getId(), UUID.randomUUID()));
        qrAuthentiquePour(ticket);

        ResultatScan resultat = validateScan.execute("qr", ticket.getEventId());

        assertThat(resultat.issue()).isEqualTo(ResultatScan.Issue.ACCEPTE);
        assertThat(lireBillet(ticket.getId()).getStatus()).isEqualTo(TicketStatus.SCANNE);
        assertThat(versionEnBase("tickets", ticket.getId())).isEqualTo(1);
    }

    /**
     * 8 agents scannent le même billet au même instant (QR copié et présenté à
     * plusieurs portes). Un seul passe. Les autres voient soit DEJA_SCANNE (ils
     * ont lu le billet après la validation du premier), soit un conflit de
     * version (ils l'ont lu EMIS avant : c'est le @Version qui les arrête, 409 à
     * lire comme « déjà scanné »). Jamais deux entrées pour un billet.
     */
    @Test
    void des_scans_simultanes_du_meme_billet_ne_laissent_entrer_qu_une_fois() throws Exception {
        TicketCategory category = categorieEnregistree(10);
        Ticket ticket = enregistrer(new Ticket(category.getEventId(), category.getId(), UUID.randomUUID()));
        qrAuthentiquePour(ticket);

        List<Object> resultats = enParallele(THREADS, i -> () -> validateScan.execute("qr", ticket.getEventId()));

        assertThat(resultats).filteredOn(r -> r instanceof ResultatScan rs && rs.issue() == ResultatScan.Issue.ACCEPTE)
            .hasSize(1);
        assertThat(resultats).filteredOn(r -> !(r instanceof ResultatScan rs && rs.issue() == ResultatScan.Issue.ACCEPTE))
            .hasSize(THREADS - 1)
            .allSatisfy(r -> {
                if (r instanceof ResultatScan rs) {
                    assertThat(rs.issue()).isEqualTo(ResultatScan.Issue.DEJA_SCANNE);
                } else {
                    assertThat(r).isInstanceOf(OptimisticLockingFailureException.class);
                }
            });
        assertThat(lireBillet(ticket.getId()).getStatus()).isEqualTo(TicketStatus.SCANNE);
        assertThat(versionEnBase("tickets", ticket.getId())).isEqualTo(1);  // une seule écriture validée
    }

    /** Le signataire est simulé ici (la vraie signature a ses tests) : il « vérifie » le QR de ce billet. */
    private void qrAuthentiquePour(Ticket ticket) {
        when(qrSigner.verify(any(String.class))).thenReturn(QrVerificationResult.valide(
            ticket.getId(), ticket.getEventId(), ticket.getEmisLe().truncatedTo(ChronoUnit.MILLIS)));
    }
}
