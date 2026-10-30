package com.billetplus;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.usecase.CancelEventTicketsUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base commune des tests du contexte COMPLET (@SpringBootTest + MockMvc), sur
 * un vrai PostgreSQL (PostGIS) et un vrai Redis.
 *
 * Conteneurs « singletons » : démarrés une seule fois pour tout le lancement
 * (bloc statique), jamais par @Container. Avec @Container, chaque classe de
 * test arrêterait ses conteneurs à la fin, alors que Spring réutilise le
 * contexte mis en cache, qui pointerait vers des conteneurs arrêtés. Ryuk
 * (Testcontainers) les supprime à la fin du lancement.
 *
 * Toutes les classes filles déclarent exactement les mêmes @MockBean et
 * @SpyBean (ceux d'ici, et aucun autre) : Spring réutilise alors un seul contexte
 * pour toutes, au lieu d'en démarrer un par classe (~25 s chacun).
 *
 * Seuls le signataire Ed25519 (clé privée gitignorée, absente en CI) et la
 * passerelle de paiement (UnitechPay, sans environnement de test : ADR 0007)
 * sont simulés.
 * Sans Docker : conteneurs non démarrés, tests ignorés (disabledWithoutDocker).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContexteCompletTestBase.AdresseIpAleatoire.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class ContexteCompletTestBase {

    /**
     * Chaque requête MockMvc reçoit par défaut une adresse IP aléatoire : sinon,
     * toutes les classes du contexte partagé viendraient de 127.0.0.1 et
     * épuiseraient ensemble les limites par IP (5 inscriptions par 10 minutes...).
     * Un test qui veut une IP précise la fixe lui-même (son RequestPostProcessor
     * s'applique après celui-ci).
     */
    @TestConfiguration
    static class AdresseIpAleatoire {
        @Bean
        MockMvcBuilderCustomizer adresseIpAleatoireParRequete() {
            return builder -> builder.defaultRequest(get("/").with(requete -> {
                ThreadLocalRandom aleatoire = ThreadLocalRandom.current();
                requete.setRemoteAddr("10." + aleatoire.nextInt(256) + "." + aleatoire.nextInt(256) + "."
                    + aleatoire.nextInt(256));
                return requete;
            }));
        }
    }

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            REDIS.start();
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockBean
    protected QRSigner qrSigner;
    @MockBean
    protected PasserellePaiement passerellePaiement;
    /** Espion sur le vrai cas d'usage : permet de simuler une panne pendant l'annulation. */
    @SpyBean
    protected CancelEventTicketsUseCase cancelEventTickets;

    @Autowired
    protected MockMvc mvc;

    /** Même authentification que JwtAuthenticationFilter : principal = UUID, autorité ROLE_<rôle>. */
    protected static RequestPostProcessor connecte(UUID userId, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
