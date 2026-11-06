package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Event;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.TauxCommission;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commission et reversements (ADR 0009) sur un vrai PostgreSQL : droits par rôle, montants,
 * reste à reverser, événement annulé, refus de saisie. Les commandes payées sont écrites
 * par le port du module (le paiement réel est couvert par ses propres tests).
 */
class RecettesEtReversementsHttpIntegrationTest extends ContexteCompletTestBase {

    @Autowired
    private AuthService authService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private EventService eventService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID admin = UUID.randomUUID();

    private UUID organisateur() {
        return authService.registerOrganisateur("orga-" + UUID.randomUUID() + "@billetplus.sn", "motdepasse", "Orga").getId();
    }

    private Event evenementPublie(UUID orga) {
        UUID lieu = venueService.create("Sorano", "Dakar", null, null).getId();
        Event e = eventService.create("Concert", null, Instant.now().plus(10, ChronoUnit.DAYS), null, lieu, orga, null);
        eventService.publier(e.getId(), orga);
        return e;
    }

    /** Commande payée de ce montant, au taux donné, écrite directement par le port du module. */
    private void commandePayee(Event e, long montant, int tauxPointsBase) {
        Instant maintenant = Instant.now();
        Order commande = Order.creer(e.getId(), NumeroTelephone.depuis("77 123 45 67"),
            List.of(new OrderLine(UUID.randomUUID(), 1, Money.ofFcfa(montant))), maintenant, Duration.ofMinutes(4),
            new TauxCommission(tauxPointsBase)).commande();
        commande.marquerPayee(maintenant.plusSeconds(30));
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> orderRepository.save(commande));
    }

    private String reversement(UUID eventId, long montant, String reference, String motif) {
        return "{\"eventId\":\"" + eventId + "\",\"montantFcfa\":" + montant + ",\"reference\":\"" + reference + "\","
            + (motif == null ? "" : "\"motif\":\"" + motif + "\",") + "\"reverseLe\":\"" + LocalDate.now() + "\"}";
    }

    @Test
    void l_organisateur_voit_brut_commission_et_net_et_personne_d_autre() throws Exception {
        UUID orga = organisateur();
        Event e = evenementPublie(orga);
        commandePayee(e, 3_333, 500);   // commission 166
        commandePayee(e, 10_000, 300);  // commission 300 (taux figé différent)

        mvc.perform(get("/api/events/{id}/recettes", e.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commandes").value(2))
            .andExpect(jsonPath("$.brutFcfa").value(13_333))
            .andExpect(jsonPath("$.commissionFcfa").value(466))
            .andExpect(jsonPath("$.netFcfa").value(12_867))
            .andExpect(jsonPath("$.reverseFcfa").value(0))
            .andExpect(jsonPath("$.resteAReverserFcfa").value(12_867));

        mvc.perform(get("/api/events/{id}/recettes", e.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events/{id}/recettes", e.getId()).with(connecte(organisateur(), "ORGANISATEUR")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/events/{id}/recettes", e.getId()).with(connecte(admin, "ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void l_administrateur_enregistre_un_reversement_et_le_reste_diminue() throws Exception {
        UUID orga = organisateur();
        Event e = evenementPublie(orga);
        commandePayee(e, 20_000, 500);  // net 19 000

        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), 15_000, "WAVE-ACOMPTE", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.montantFcfa").value(15_000))
            .andExpect(jsonPath("$.reference").value("WAVE-ACOMPTE"));

        mvc.perform(get("/api/admin/reversements").with(connecte(admin, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.eventId == '" + e.getId() + "')].recettes.resteAReverserFcfa").value(4_000));
        mvc.perform(get("/api/events/{id}/recettes", e.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(jsonPath("$.reverseFcfa").value(15_000))
            .andExpect(jsonPath("$.resteAReverserFcfa").value(4_000));
    }

    @Test
    void un_evenement_annule_apres_un_reversement_montre_le_trop_reverse() throws Exception {
        UUID orga = organisateur();
        Event e = evenementPublie(orga);
        commandePayee(e, 10_000, 500);
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), 9_500, "WAVE-1", null)))
            .andExpect(status().isCreated());

        eventService.annuler(e.getId(), orga);

        mvc.perform(get("/api/events/{id}/recettes", e.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(jsonPath("$.netFcfa").value(0))
            .andExpect(jsonPath("$.commissionFcfa").value(0))
            .andExpect(jsonPath("$.resteAReverserFcfa").value(-9_500));
    }

    @Test
    void un_meme_virement_ne_s_enregistre_qu_une_fois() throws Exception {
        UUID orga = organisateur();
        Event e = evenementPublie(orga);
        commandePayee(e, 20_000, 500);

        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), 19_000, "WAVE-UNIQUE", null)))
            .andExpect(status().isCreated());
        // Rejeu (réponse perdue, double clic) ou second administrateur : refusé, rien n'est compté deux fois.
        mvc.perform(post("/api/admin/reversements").with(connecte(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), 19_000, "WAVE-UNIQUE", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Ce virement est déjà enregistré")));
        // Même virement saisi autrement (minuscules, espaces) : aussi refusé.
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), 19_000, "wave-unique ", null)))
            .andExpect(status().isConflict());

        mvc.perform(get("/api/events/{id}/recettes", e.getId()).with(connecte(orga, "ORGANISATEUR")))
            .andExpect(jsonPath("$.reverseFcfa").value(19_000))
            .andExpect(jsonPath("$.resteAReverserFcfa").value(0));
    }

    @Test
    void aucun_virement_positif_vers_un_evenement_annule() throws Exception {
        UUID orga = organisateur();
        Event e = evenementPublie(orga);
        commandePayee(e, 10_000, 500);
        eventService.annuler(e.getId(), orga);

        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), 9_500, "WAVE-X", null)))
            .andExpect(status().isConflict());
    }

    @Test
    void les_saisies_invalides_et_les_mauvais_roles_sont_refuses() throws Exception {
        UUID orga = organisateur();
        Event e = evenementPublie(orga);
        commandePayee(e, 10_000, 500);

        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), -500, "CORR", null)))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), 500, "R".repeat(101), null)))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(UUID.randomUUID(), 500, "R", null)))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), -500, "CORR", "Doublon")))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/admin/reversements").with(connecte(orga, "ORGANISATEUR"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reversements").with(connecte(orga, "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content(reversement(e.getId(), 500, "R", null)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reversements")).andExpect(status().isUnauthorized());
    }
}
