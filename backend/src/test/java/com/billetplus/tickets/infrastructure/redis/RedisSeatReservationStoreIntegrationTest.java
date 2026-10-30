package com.billetplus.tickets.infrastructure.redis;

import com.billetplus.tickets.domain.ReservationTemporaire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration de l'adaptateur Redis, sur un vrai Redis
 * (Testcontainers, même image que infra/docker-compose.yml). La CI n'a pas de
 * service Redis : ce conteneur est le seul Redis des tests.
 *
 * L'heure étant passée en paramètre, l'expiration se teste sans attendre.
 * Après chaque test, le total courant doit être égal à la somme réelle des
 * quantités : c'est l'invariant que les scripts maintiennent.
 *
 * Ignorés (et non en échec) si Docker n'est pas disponible ; en CI, l'étape
 * « Refuser les tests ignorés » ferait alors échouer le run.
 */
@DataRedisTest
@Import(RedisSeatReservationStore.class)
@Testcontainers(disabledWithoutDocker = true)
class RedisSeatReservationStoreIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final Duration CINQ_MINUTES = Duration.ofMinutes(5);

    @Autowired
    private RedisSeatReservationStore store;
    @Autowired
    private StringRedisTemplate redis;

    private UUID categoryId;
    private Instant maintenant;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        categoryId = UUID.randomUUID();
        maintenant = Instant.now();
    }

    @AfterEach
    void leTotalCourantResteEgalALaSommeReelle() {
        assertThat(totalCourant()).isEqualTo(sommeReelle());
    }

    private ReservationTemporaire reservation(UUID orderId, int quantite) {
        return ReservationTemporaire.creer(categoryId, orderId, quantite, maintenant, CINQ_MINUTES);
    }

    private Integer quantiteStockee(UUID orderId) {
        Object valeur = redis.opsForHash().get(RedisSeatReservationStore.cleQuantites(categoryId), orderId.toString());
        return valeur == null ? null : Integer.valueOf((String) valeur);
    }

    private Double expirationStockee(UUID orderId) {
        return redis.opsForZSet().score(RedisSeatReservationStore.cleReservations(categoryId), orderId.toString());
    }

    private long nombreDeReservations() {
        return redis.opsForZSet().zCard(RedisSeatReservationStore.cleReservations(categoryId));
    }

    private int sommeReelle() {
        return redis.opsForHash().values(RedisSeatReservationStore.cleQuantites(categoryId)).stream()
            .mapToInt(v -> Integer.parseInt((String) v)).sum();
    }

    private int totalCourant() {
        String total = redis.opsForValue().get(RedisSeatReservationStore.cleTotal(categoryId));
        return total == null ? 0 : Integer.parseInt(total);
    }

    @Test
    void les_places_reservees_ne_comptent_que_les_reservations_encore_valables() {
        assertThat(store.placesReservees(categoryId, maintenant)).isZero();
        store.reserver(reservation(UUID.randomUUID(), 3), 100, maintenant);
        store.reserver(reservation(UUID.randomUUID(), 2), 100, maintenant);

        assertThat(store.placesReservees(categoryId, maintenant)).isEqualTo(5);
        // À l'instant d'expiration (inclus), et après : plus rien, même avant tout nettoyage.
        assertThat(store.placesReservees(categoryId, maintenant.plus(CINQ_MINUTES))).isZero();
        assertThat(nombreDeReservations()).isEqualTo(2);
    }

    @Test
    void enregistre_une_reservation_qui_tient_dans_la_limite_et_renvoie_son_expiration() {
        UUID orderId = UUID.randomUUID();
        ReservationTemporaire reservation = reservation(orderId, 3);

        Optional<Instant> expiration = store.reserver(reservation, 5, maintenant);

        assertThat(expiration).contains(reservation.getExpireLe());
        assertThat(quantiteStockee(orderId)).isEqualTo(3);
        assertThat(expirationStockee(orderId)).isEqualTo((double) reservation.getExpireLe().toEpochMilli());
        assertThat(totalCourant()).isEqualTo(3);
    }

    @Test
    void refuse_une_reservation_qui_depasserait_la_limite_sans_rien_ecrire() {
        UUID premiere = UUID.randomUUID();
        UUID seconde = UUID.randomUUID();
        store.reserver(reservation(premiere, 3), 5, maintenant);

        assertThat(store.reserver(reservation(seconde, 3), 5, maintenant)).isEmpty();

        assertThat(quantiteStockee(seconde)).isNull();
        assertThat(nombreDeReservations()).isEqualTo(1);
        assertThat(totalCourant()).isEqualTo(3);
    }

    @Test
    void accepte_une_reservation_qui_atteint_exactement_la_limite() {
        store.reserver(reservation(UUID.randomUUID(), 3), 5, maintenant);

        assertThat(store.reserver(reservation(UUID.randomUUID(), 2), 5, maintenant)).isPresent();
        assertThat(totalCourant()).isEqualTo(5);
    }

    /** La même commande réserve de nouveau (changement de quantité) : remplacement, pas cumul. */
    @Test
    void remplace_la_quantite_de_la_meme_commande_au_lieu_de_la_cumuler() {
        UUID orderId = UUID.randomUUID();
        store.reserver(reservation(orderId, 3), 5, maintenant);

        assertThat(store.reserver(reservation(orderId, 4), 5, maintenant)).isPresent();

        assertThat(quantiteStockee(orderId)).isEqualTo(4);
        assertThat(nombreDeReservations()).isEqualTo(1);
        assertThat(totalCourant()).isEqualTo(4);
    }

    /**
     * Relecture sécurité : re-réserver toutes les 4 minutes ne doit pas bloquer
     * les places indéfiniment. Le remplacement garde l'expiration d'origine.
     */
    @Test
    void un_remplacement_ne_prolonge_jamais_l_expiration_d_origine() {
        UUID orderId = UUID.randomUUID();
        ReservationTemporaire premiere = reservation(orderId, 2);
        store.reserver(premiere, 5, maintenant);
        Instant quatreMinutesPlusTard = maintenant.plus(Duration.ofMinutes(4));

        Optional<Instant> expiration = store.reserver(
            ReservationTemporaire.creer(categoryId, orderId, 3, quatreMinutesPlusTard, CINQ_MINUTES), 5, quatreMinutesPlusTard);

        assertThat(expiration).contains(premiere.getExpireLe());
        assertThat(expirationStockee(orderId)).isEqualTo((double) premiere.getExpireLe().toEpochMilli());
        assertThat(quantiteStockee(orderId)).isEqualTo(3);
    }

    /** Une fois expirée, une nouvelle réservation de la même commande repart avec une nouvelle expiration. */
    @Test
    void une_commande_dont_la_reservation_a_expire_obtient_une_nouvelle_expiration() {
        UUID orderId = UUID.randomUUID();
        store.reserver(ReservationTemporaire.creer(categoryId, orderId, 2, maintenant, Duration.ofSeconds(1)), 5, maintenant);
        Instant plusTard = maintenant.plusSeconds(10);
        ReservationTemporaire nouvelle = ReservationTemporaire.creer(categoryId, orderId, 3, plusTard, CINQ_MINUTES);

        assertThat(store.reserver(nouvelle, 5, plusTard)).contains(nouvelle.getExpireLe());
        assertThat(totalCourant()).isEqualTo(3);
    }

    /** Sans job de nettoyage : une réservation expirée ne compte plus et disparaît à la réservation suivante. */
    @Test
    void ne_compte_plus_une_reservation_expiree_et_la_retire() {
        UUID expiree = UUID.randomUUID();
        store.reserver(ReservationTemporaire.creer(categoryId, expiree, 4, maintenant, Duration.ofSeconds(1)), 5, maintenant);
        Instant deuxSecondesPlusTard = maintenant.plusSeconds(2);
        UUID nouvelle = UUID.randomUUID();

        Optional<Instant> acceptee = store.reserver(
            ReservationTemporaire.creer(categoryId, nouvelle, 4, deuxSecondesPlusTard, CINQ_MINUTES), 5, deuxSecondesPlusTard);

        assertThat(acceptee).isPresent();
        assertThat(quantiteStockee(expiree)).isNull();
        assertThat(quantiteStockee(nouvelle)).isEqualTo(4);
        assertThat(nombreDeReservations()).isEqualTo(1);
        assertThat(totalCourant()).isEqualTo(4);
    }

    @Test
    void une_reservation_encore_valide_continue_de_compter() {
        store.reserver(ReservationTemporaire.creer(categoryId, UUID.randomUUID(), 4, maintenant, Duration.ofSeconds(10)), 5, maintenant);

        assertThat(store.reserver(reservation(UUID.randomUUID(), 2), 5, maintenant.plusSeconds(9))).isEmpty();
    }

    /**
     * Le nettoyage est borné par appel (pour borner la durée du script). Les
     * réservations expirées au-delà du lot restent comptées : erreur prudente
     * (place refusée à tort), jamais une survente ; elles partent aux appels suivants.
     */
    @Test
    void retire_au_plus_un_lot_de_reservations_expirees_par_appel() {
        int expirees = RedisSeatReservationStore.NETTOYAGE_MAX_PAR_APPEL + 50;
        for (int i = 0; i < expirees; i++) {
            store.reserver(ReservationTemporaire.creer(categoryId, UUID.randomUUID(), 1, maintenant, Duration.ofSeconds(1)),
                1_000, maintenant);
        }
        Instant plusTard = maintenant.plusSeconds(5);

        store.reserver(ReservationTemporaire.creer(categoryId, UUID.randomUUID(), 1, plusTard, CINQ_MINUTES), 1_000, plusTard);
        assertThat(nombreDeReservations()).isEqualTo(50 + 1);

        store.reserver(ReservationTemporaire.creer(categoryId, UUID.randomUUID(), 1, plusTard, CINQ_MINUTES), 1_000, plusTard);
        assertThat(nombreDeReservations()).isEqualTo(2);
        assertThat(totalCourant()).isEqualTo(2);
    }

    /** Une catégorie qui ne reçoit plus de demandes ne laisse rien : Redis supprime ses clés. */
    @Test
    void pose_une_expiration_redis_sur_les_trois_cles_de_la_categorie() {
        store.reserver(reservation(UUID.randomUUID(), 1), 5, maintenant);

        // 5 minutes de réservation + 1 minute de marge, à quelques secondes près.
        for (String cle : List.of(RedisSeatReservationStore.cleReservations(categoryId),
                                  RedisSeatReservationStore.cleQuantites(categoryId),
                                  RedisSeatReservationStore.cleTotal(categoryId))) {
            assertThat(redis.getExpire(cle, TimeUnit.SECONDS)).as(cle).isBetween(350L, 360L);
        }
    }

    @Test
    void les_categories_sont_independantes() {
        store.reserver(reservation(UUID.randomUUID(), 5), 5, maintenant);
        UUID autreCategorie = UUID.randomUUID();

        assertThat(store.reserver(
            ReservationTemporaire.creer(autreCategorie, UUID.randomUUID(), 5, maintenant, CINQ_MINUTES), 5, maintenant)).isPresent();
    }

    @Test
    void liberer_rend_les_places_et_est_sans_effet_sur_une_reservation_absente() {
        UUID orderId = UUID.randomUUID();
        store.reserver(reservation(orderId, 5), 5, maintenant);

        store.liberer(categoryId, orderId);
        store.liberer(categoryId, orderId);            // déjà libérée
        store.liberer(categoryId, UUID.randomUUID());  // jamais réservée

        assertThat(quantiteStockee(orderId)).isNull();
        assertThat(nombreDeReservations()).isZero();
        assertThat(totalCourant()).isZero();
        assertThat(store.reserver(reservation(UUID.randomUUID(), 5), 5, maintenant)).isPresent();
    }

    /**
     * 8 commandes simultanées de 3 places pour une limite de 10 : le script
     * étant atomique, exactement 3 passent, quel que soit l'ordre d'arrivée.
     * Une sonde temporaire a vérifié que les 8 appels étaient partis avant la
     * première réponse (concurrence réelle).
     */
    @Test
    void des_reservations_simultanees_ne_depassent_jamais_la_limite() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch prets = new CountDownLatch(threads);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futurs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                ReservationTemporaire reservation = reservation(UUID.randomUUID(), 3);
                Callable<Boolean> appel = () -> {
                    prets.countDown();
                    depart.await();
                    return store.reserver(reservation, 10, maintenant).isPresent();
                };
                futurs.add(pool.submit(appel));
            }
            assertThat(prets.await(10, TimeUnit.SECONDS)).isTrue();
            depart.countDown();
            long acceptees = 0;
            for (Future<Boolean> futur : futurs) {
                if (futur.get(30, TimeUnit.SECONDS)) {
                    acceptees++;
                }
            }

            assertThat(acceptees).isEqualTo(3);
            assertThat(totalCourant()).isEqualTo(9);
            assertThat(nombreDeReservations()).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }
}
