package com.billetplus.shared.limitation;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compteur de limitation sur un vrai Redis (Testcontainers, même image que
 * infra/docker-compose.yml). Ignorés si Docker n'est pas disponible.
 */
@DataRedisTest
@Import(RedisCompteurRequetes.class)
@Testcontainers(disabledWithoutDocker = true)
class RedisCompteurRequetesIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private RedisCompteurRequetes compteur;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    void le_compteur_s_incremente_et_expire_avec_sa_fenetre() {
        String cle = "limitation:test:" + UUID.randomUUID();

        assertThat(compteur.incrementer(cle, Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(compteur.incrementer(cle, Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(compteur.incrementer(cle, Duration.ofMinutes(1))).isEqualTo(3);

        Long ttl = redis.getExpire(cle, TimeUnit.MILLISECONDS);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(60_000);
    }

    @Test
    void l_expiration_n_est_posee_qu_au_premier_appel() throws Exception {
        String cle = "limitation:test:" + UUID.randomUUID();
        compteur.incrementer(cle, Duration.ofSeconds(2));
        Thread.sleep(1_100);
        compteur.incrementer(cle, Duration.ofSeconds(2));

        // Sans cette règle, chaque appel repousserait l'expiration et un client
        // régulier ne verrait jamais son compteur se vider.
        assertThat(redis.getExpire(cle, TimeUnit.MILLISECONDS)).isLessThan(1_000);
    }

    @Test
    void des_appels_simultanes_sont_tous_comptes() throws Exception {
        String cle = "limitation:test:" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Long>> appels = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                appels.add(() -> compteur.incrementer(cle, Duration.ofMinutes(1)));
            }
            List<Long> totaux = new ArrayList<>();
            for (Future<Long> resultat : pool.invokeAll(appels)) {
                totaux.add(resultat.get());
            }
            // Chaque appel a reçu un total distinct : aucun incrément perdu.
            assertThat(totaux).doesNotHaveDuplicates().hasSize(200);
            assertThat(redis.opsForValue().get(cle)).isEqualTo("200");
        } finally {
            pool.shutdownNow();
        }
    }
}
