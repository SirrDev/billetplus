package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.domain.MotifSansBillet;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.OrderStatus;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistance des commandes (V8) sur un vrai PostgreSQL (Testcontainers), avec
 * les migrations Flyway réelles. Même organisation que TicketPersistenceIntegrationTest :
 * pas de transaction automatique par test (NOT_SUPPORTED), transactions ouvertes
 * explicitement, pour pouvoir en faire chevaucher deux.
 *
 * Ignorés (et non en échec) si Docker n'est pas disponible.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaOrderRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class OrderPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Instant MAINTENANT = Instant.parse("2026-11-01T10:00:00.123456789Z");

    @Autowired
    private JpaOrderRepositoryAdapter commandes;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private <T> T enTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(statut -> action.get());
    }

    private static Order.CommandeCreee nouvelle() {
        return Order.creer(UUID.randomUUID(), NumeroTelephone.depuis("77 123 45 67"),
            List.of(new OrderLine(UUID.randomUUID(), 2, Money.ofFcfa(5000)),
                    new OrderLine(UUID.randomUUID(), 1, Money.ofFcfa(15000))),
            MAINTENANT, Duration.ofMinutes(4));
    }

    private Order enregistrer(Order commande) {
        return enTransaction(() -> commandes.save(commande));
    }

    @Test
    void une_commande_enregistree_se_relit_a_l_identique_avec_ses_lignes() {
        Order.CommandeCreee creee = nouvelle();
        enregistrer(creee.commande());

        // Lecture hors transaction : les lignes (LAZY) doivent être chargées par l'EntityGraph.
        Order relue = commandes.findById(creee.commande().getId()).orElseThrow();

        assertThat(relue).usingRecursiveComparison().isEqualTo(creee.commande());
        assertThat(relue.getLignes()).hasSize(2);
        assertThat(relue.montantTotal()).isEqualTo(Money.ofFcfa(25000));
        assertThat(relue.accepteLeJeton(creee.jeton().enClair())).isTrue();
    }

    @Test
    void la_base_ne_contient_que_l_empreinte_du_jeton() {
        Order.CommandeCreee creee = nouvelle();
        enregistrer(creee.commande());

        String stockee = jdbc.queryForObject("SELECT empreinte_jeton FROM orders WHERE id = ?", String.class,
            creee.commande().getId());
        assertThat(stockee).hasSize(64).isNotEqualTo(creee.jeton().enClair());
        Integer avecLeJeton = jdbc.queryForObject(
            "SELECT count(*) FROM orders WHERE empreinte_jeton = ? OR telephone = ?", Integer.class,
            creee.jeton().enClair(), creee.jeton().enClair());
        assertThat(avecLeJeton).isZero();
    }

    @Test
    void une_transition_est_persistee_et_les_lignes_restent_intactes() {
        Order commande = nouvelle().commande();
        enregistrer(commande);

        enTransaction(() -> {
            Order chargee = commandes.findById(commande.getId()).orElseThrow();
            chargee.marquerPayeeSansBillet(MotifSansBillet.VENTE_FERMEE, MAINTENANT.plusSeconds(30));
            return commandes.save(chargee);
        });

        Order relue = commandes.findById(commande.getId()).orElseThrow();
        assertThat(relue.getStatus()).isEqualTo(OrderStatus.PAYEE_SANS_BILLET);
        assertThat(relue.getMotifSansBillet()).isEqualTo(MotifSansBillet.VENTE_FERMEE);
        assertThat(relue.getPayeeLe()).isEqualTo(Instant.parse("2026-11-01T10:00:30.123456Z"));
        assertThat(relue.getLignes()).isEqualTo(commande.getLignes());
        Long version = jdbc.queryForObject("SELECT version FROM orders WHERE id = ?", Long.class, commande.getId());
        assertThat(version).isEqualTo(1L);
    }

    /**
     * Deux confirmations de la même commande qui se chevauchent : A lit la version 0,
     * B (transaction indépendante, REQUIRES_NEW) lit la même version, paie et valide
     * pendant que A est encore ouverte. A échoue à la validation au lieu d'écraser B.
     */
    @Test
    void deux_transitions_concurrentes_sur_la_meme_version_ne_passent_pas_toutes_les_deux() {
        Order commande = nouvelle().commande();
        enregistrer(commande);
        TransactionTemplate independante = new TransactionTemplate(transactionManager);
        independante.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> enTransaction(() -> {
            Order lueParA = commandes.findById(commande.getId()).orElseThrow();
            independante.execute(statut -> {
                Order lueParB = commandes.findById(commande.getId()).orElseThrow();
                lueParB.marquerPayee(MAINTENANT.plusSeconds(10));
                return commandes.save(lueParB);
            });
            lueParA.annuler();
            return commandes.save(lueParA);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(commandes.findById(commande.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAYEE);
    }

    @Test
    void enregistrer_hors_transaction_est_refuse() {
        assertThatThrownBy(() -> commandes.save(nouvelle().commande()))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void une_commande_inconnue_est_absente() {
        assertThat(commandes.findById(UUID.randomUUID())).isEmpty();
    }

    // ---- Contraintes de V8 : le dernier rempart, indépendant du code applicatif ----

    private void insererCommande(UUID id, String telephone, String statut, Instant payeeLe, String motif) {
        jdbc.update("INSERT INTO orders (id, event_id, telephone, empreinte_jeton, status, cree_le, expire_le, "
                + "payee_le, motif_sans_billet) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, UUID.randomUUID(), telephone, "a".repeat(64), statut, Timestamp.from(MAINTENANT),
            Timestamp.from(MAINTENANT.plusSeconds(240)), payeeLe == null ? null : Timestamp.from(payeeLe), motif);
    }

    @Test
    void la_base_refuse_une_commande_payee_sans_date_ou_a_rembourser_sans_motif() {
        assertThatThrownBy(() -> insererCommande(UUID.randomUUID(), "+221771234567", "PAYEE", null, null))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insererCommande(UUID.randomUUID(), "+221771234567", "PAYEE_SANS_BILLET",
                MAINTENANT, null))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insererCommande(UUID.randomUUID(), "+221771234567", "EN_ATTENTE_PAIEMENT",
                MAINTENANT, null))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insererCommande(UUID.randomUUID(), "+221771234567", "PAYEE", MAINTENANT,
                "VENTE_FERMEE"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void la_base_refuse_un_telephone_non_normalise() {
        assertThatThrownBy(() -> insererCommande(UUID.randomUUID(), "771234567", "EN_ATTENTE_PAIEMENT", null, null))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insererCommande(UUID.randomUUID(), "+221338234567", "EN_ATTENTE_PAIEMENT", null, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void la_base_refuse_une_ligne_hors_bornes_ou_une_categorie_en_double() {
        UUID commande = UUID.randomUUID();
        insererCommande(commande, "+221771234567", "EN_ATTENTE_PAIEMENT", null, null);
        UUID categorie = UUID.randomUUID();
        String ligne = "INSERT INTO order_lines (order_id, category_id, quantite, prix_unitaire_fcfa) VALUES (?, ?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(ligne, commande, UUID.randomUUID(), 21, 5000))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(ligne, commande, UUID.randomUUID(), 0, 5000))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(ligne, commande, UUID.randomUUID(), 1, -1))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(ligne, UUID.randomUUID(), UUID.randomUUID(), 1, 5000))
            .as("commande inexistante : clé étrangère interne au module")
            .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(ligne, commande, categorie, 20, 5000);
        assertThatThrownBy(() -> jdbc.update(ligne, commande, categorie, 1, 5000))
            .isInstanceOf(DataIntegrityViolationException.class);

        List<Map<String, Object>> lignes = jdbc.queryForList("SELECT * FROM order_lines WHERE order_id = ?", commande);
        assertThat(lignes).hasSize(1);
    }
}
