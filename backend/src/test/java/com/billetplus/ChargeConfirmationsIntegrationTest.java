package com.billetplus;

import com.billetplus.orders.application.port.NotificationPaiement;
import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.orders.application.usecase.ConfirmerPaiementUseCase;
import com.billetplus.orders.application.usecase.CreateOrderUseCase;
import com.billetplus.orders.application.usecase.OuvrirPaiementUseCase;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mesure de charge (inventaire n° 16) : l'ouverture de la billetterie, quand de
 * nombreux paiements sont confirmés EN MÊME TEMPS sur la même catégorie.
 *
 * Exigence : chaque paiement confirmé donne ses billets. Un conflit qui épuise ses
 * tentatives répond 503 à la passerelle ; tant que sa politique de relance est
 * inconnue (ADR 0007), c'est un client payé sans billet. Le test compte et
 * journalise chaque issue, pour que la mesure reste lisible dans le rapport.
 */
class ChargeConfirmationsIntegrationTest extends ContexteCompletTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(ChargeConfirmationsIntegrationTest.class);
    private static final int CONFIRMATIONS_SIMULTANEES = 40;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CreateOrderUseCase creerCommande;
    @Autowired
    private OuvrirPaiementUseCase ouvrirPaiement;
    @Autowired
    private ConfirmerPaiementUseCase confirmerPaiement;

    @Test
    void quarante_paiements_confirmes_en_meme_temps_sur_une_categorie_donnent_tous_leurs_billets() throws Exception {
        when(qrSigner.sign(any(), any(), any())).thenAnswer(i -> "qr-" + i.getArgument(0));
        AtomicInteger numero = new AtomicInteger();
        when(passerellePaiement.ouvrir(any())).thenAnswer(i -> {
            String reference = "charge_" + UUID.randomUUID();
            return new PasserellePaiement.PaiementCree(reference, numero.incrementAndGet(), "https://pay.wave.com/c/" + reference);
        });

        UUID lieu = UUID.randomUUID();
        jdbc.update("INSERT INTO venues (id, nom, adresse, ville_code) VALUES (?, 'Stade', 'Dakar', 'SN-DAKAR')", lieu);
        UUID evenement = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, titre, debute_le, venue_id, organizer_id, status) VALUES (?, ?, ?, ?, ?, 'PUBLIE')",
            evenement, "Finale " + evenement, Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS)), lieu, UUID.randomUUID());
        UUID categorie = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_categories (id, event_id, nom, prix_fcfa, quota_total) VALUES (?, ?, 'Pelouse', 2000, 1000)",
            categorie, evenement);

        // 40 commandes de 2 places (40 numéros différents : limite de 3 par numéro), paiements ouverts.
        List<String> references = new ArrayList<>();
        for (int i = 0; i < CONFIRMATIONS_SIMULTANEES; i++) {
            Order.CommandeCreee creee = creerCommande.execute(evenement, "77" + (1_000_000 + i),
                List.of(new CreateOrderUseCase.LigneDemandee(categorie, 2)));
            references.add(ouvrirPaiement.execute(creee.commande().getId(), creee.jeton().enClair(), MoyenPaiement.WAVE)
                .url().substring("https://pay.wave.com/c/".length()));
        }

        Map<String, AtomicInteger> issues = new ConcurrentHashMap<>();
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONFIRMATIONS_SIMULTANEES);
        long debut;
        try {
            List<Callable<Void>> confirmations = new ArrayList<>();
            for (String reference : references) {
                confirmations.add(() -> {
                    depart.await();
                    String issue;
                    try {
                        issue = confirmerPaiement.traiter(
                            new NotificationPaiement(NotificationPaiement.Type.PAYE, reference, 4000)).name();
                    } catch (RuntimeException e) {
                        issue = e.getClass().getSimpleName();
                    }
                    issues.computeIfAbsent(issue, k -> new AtomicInteger()).incrementAndGet();
                    return null;
                });
            }
            List<Future<Void>> resultats = new ArrayList<>();
            for (Callable<Void> confirmation : confirmations) {
                resultats.add(pool.submit(confirmation));
            }
            debut = System.nanoTime();
            depart.countDown();
            for (Future<Void> resultat : resultats) {
                resultat.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        long dureeMs = (System.nanoTime() - debut) / 1_000_000;
        LOG.info("Mesure n° 16 : {} confirmations simultanées en {} ms : {}", CONFIRMATIONS_SIMULTANEES, dureeMs, issues);

        Integer vendus = jdbc.queryForObject("SELECT quota_vendu FROM ticket_categories WHERE id = ?", Integer.class, categorie);
        Integer billets = jdbc.queryForObject("SELECT count(*) FROM tickets WHERE category_id = ?", Integer.class, categorie);
        assertThat(issues).as("issues des confirmations").containsOnlyKeys("BILLETS_EMIS");
        assertThat(issues.get("BILLETS_EMIS").get()).isEqualTo(CONFIRMATIONS_SIMULTANEES);
        assertThat(vendus).isEqualTo(2 * CONFIRMATIONS_SIMULTANEES);
        assertThat(billets).isEqualTo(2 * CONFIRMATIONS_SIMULTANEES);
    }
}
