package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.VillesDeTest;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.TauxCommission;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérification des organisateurs (ADR 0012) sur la vraie pile : déclaration, pièces
 * chiffrées, demande, décision, publication réservée aux comptes vérifiés, suspension
 * (compte et événement), conditions du reversement.
 */
class VerificationOrganisateursHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String MOT_DE_PASSE = "motdepasse";
    private static final byte[] PDF = "%PDF-1.4\nfausse piece d'identite\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

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
    @Autowired
    private JdbcTemplate jdbc;

    private final UUID admin = UUID.randomUUID();

    private record Orga(UUID id, String email) {
    }

    private Orga organisateur() {
        String email = "orga-" + UUID.randomUUID() + "@billetplus.sn";
        return new Orga(authService.registerOrganisateur(email, MOT_DE_PASSE, "Orga Test").getId(), email);
    }

    private Event brouillon(UUID orga) {
        UUID lieu = venueService.create("Sorano", "Plateau", null, null, VillesDeTest.DAKAR).getId();
        return eventService.create("Concert", null, Instant.now().plus(10, ChronoUnit.DAYS), null, lieu, orga, null);
    }

    private ResultActions deposer(UUID orga, String type, byte[] contenu, String nom) throws Exception {
        return mvc.perform(multipart("/api/verification/pieces/{type}", type)
            .file(new MockMultipartFile("fichier", nom, "application/octet-stream", contenu))
            .with(connecte(orga, "ORGANISATEUR")));
    }

    private void declarer(UUID orga, String numero) throws Exception {
        mvc.perform(put("/api/verification").with(connecte(orga, "ORGANISATEUR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeTitulaire\":\"PERSONNE\",\"numeroReversement\":\"" + numero
                    + "\",\"titulaireReversement\":\"Awa Diop\"}"))
            .andExpect(status().isOk());
    }

    /** Organisateur dont la demande est en revue (déclaration et deux pièces). */
    private Orga enRevue() throws Exception {
        Orga o = organisateur();
        declarer(o.id(), "77 123 45 67");
        deposer(o.id(), "IDENTITE", PDF, "cni.pdf").andExpect(status().isOk());
        deposer(o.id(), "JUSTIFICATIF_REVERSEMENT", PNG, "wave.png").andExpect(status().isOk());
        mvc.perform(post("/api/verification/soumission").with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statut").value("EN_REVUE"));
        return o;
    }

    @Test
    void parcours_complet_de_la_declaration_a_la_publication() throws Exception {
        Orga o = organisateur();
        Event e = brouillon(o.id());

        mvc.perform(get("/api/verification").with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.statut").value("A_VERIFIER"))
            .andExpect(jsonPath("$.pieces").isEmpty());
        // Non vérifié : le brouillon ne se publie pas.
        mvc.perform(post("/api/events/{id}/publier", e.getId()).with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Votre compte doit être vérifié avant de publier"));

        // Demande incomplète, puis fichier refusé (format lu dans le contenu, pas dans le nom).
        mvc.perform(post("/api/verification/soumission").with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isBadRequest());
        deposer(o.id(), "IDENTITE", "pas un pdf".getBytes(StandardCharsets.UTF_8), "cni.pdf")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Format refusé : PDF, JPEG ou PNG seulement"));
        deposer(o.id(), "INCONNU", PDF, "x.pdf").andExpect(status().isBadRequest());

        declarer(o.id(), "+221 77 123 45 67");
        deposer(o.id(), "IDENTITE", PDF, "cni.pdf").andExpect(status().isOk());
        deposer(o.id(), "JUSTIFICATIF_REVERSEMENT", PNG, "wave.png")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroReversement").value("+221771234567"))
            .andExpect(jsonPath("$.pieces.length()").value(2));
        // Chiffrée en base : jamais le contenu en clair.
        byte[] stocke = jdbc.queryForObject("SELECT contenu FROM pieces_organisateurs WHERE organisateur_id = ? "
            + "AND type = 'IDENTITE'", byte[].class, o.id());
        assertThat(stocke).isNotEqualTo(PDF);
        assertThat(new String(stocke, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");

        mvc.perform(post("/api/verification/soumission").with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("EN_REVUE"));

        // L'administrateur voit la demande et télécharge la pièce, déchiffrée à l'identique.
        mvc.perform(get("/api/admin/verifications").with(connecte(admin, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.organisateurId == '" + o.id() + "')].email").value(o.email()));
        mvc.perform(get("/api/admin/verifications/{id}/pieces/IDENTITE", o.id()).with(connecte(admin, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"identite.pdf\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().bytes(PDF));

        mvc.perform(post("/api/admin/verifications/{id}/acceptation", o.id()).with(connecte(admin, "ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VERIFIE"));
        mvc.perform(post("/api/events/{id}/publier", e.getId()).with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLIE"));

        // Nouveau numéro : retour en revue avant tout reversement.
        declarer(o.id(), "78 765 43 21");
        mvc.perform(get("/api/verification").with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(jsonPath("$.statut").value("EN_REVUE"));
    }

    /** Relecture de sécurité : l'administrateur décide sur ce qu'il a vu, rien ne change pendant l'examen. */
    @Test
    void pendant_l_examen_le_dossier_ne_change_plus() throws Exception {
        Orga o = enRevue();
        mvc.perform(put("/api/verification").with(connecte(o.id(), "ORGANISATEUR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeTitulaire\":\"PERSONNE\",\"numeroReversement\":\"781112233\","
                    + "\"titulaireReversement\":\"Complice\"}"))
            .andExpect(status().isConflict());
        deposer(o.id(), "JUSTIFICATIF_REVERSEMENT", PNG, "autre.png").andExpect(status().isConflict());
        mvc.perform(post("/api/admin/verifications/{id}/acceptation", o.id()).with(connecte(admin, "ADMIN")))
            .andExpect(jsonPath("$.numeroReversement").value("+221771234567"));
    }

    /** Relecture de sécurité : un événement daté dans le passé ne se publie pas (J+1 serait déjà atteint). */
    @Test
    void un_evenement_deja_commence_ne_se_publie_pas() throws Exception {
        Orga o = organisateur();
        organisateurVerifie(o.id());
        UUID lieu = venueService.create("Sorano", "Plateau", null, null, VillesDeTest.DAKAR).getId();
        Event hier = eventService.create("Concert d'hier", null, Instant.now().minus(1, ChronoUnit.DAYS), null, lieu,
            o.id(), null);
        mvc.perform(post("/api/events/{id}/publier", hier.getId()).with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Un événement déjà commencé ne peut pas être publié"));
    }

    @Test
    void un_refus_est_motive_et_permet_une_nouvelle_demande() throws Exception {
        Orga o = enRevue();
        mvc.perform(post("/api/admin/verifications/{id}/refus", o.id()).with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/verifications/{id}/refus", o.id()).with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Piece illisible\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statut").value("REFUSE"))
            .andExpect(jsonPath("$.motif").value("Piece illisible"));
        deposer(o.id(), "IDENTITE", PDF, "cni-nette.pdf").andExpect(status().isOk());
        mvc.perform(post("/api/verification/soumission").with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("EN_REVUE"))
            .andExpect(jsonPath("$.motif").doesNotExist());
    }

    @Test
    void suspendre_un_compte_bloque_la_connexion_et_annule_ses_evenements_a_venir() throws Exception {
        Orga o = organisateur();
        organisateurVerifie(o.id());
        Event publie = brouillon(o.id());
        eventService.publier(publie.getId(), o.id());
        Event brouillon = brouillon(o.id());
        String agent = mvc.perform(post("/api/agents").with(connecte(o.id(), "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"agent-" + UUID.randomUUID() + "@billetplus.sn\",\"motDePasse\":\"motdepasse\","
                    + "\"nomComplet\":\"Agent\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID agentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(agent, "$.id"));

        mvc.perform(post("/api/admin/verifications/{id}/suspension", o.id()).with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Faux evenement signale\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statut").value("SUSPENDU"));

        for (Event ev : List.of(publie, brouillon)) {
            assertThat(jdbc.queryForObject("SELECT status FROM events WHERE id = ?", String.class, ev.getId()))
                .isEqualTo("ANNULE");
            assertThat(jdbc.queryForObject("SELECT suspendu_motif FROM events WHERE id = ?", String.class, ev.getId()))
                .isEqualTo("Compte de l'organisateur suspendu par Billet+");
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + o.email() + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
            .andExpect(status().isUnauthorized());
        // Ses agents ne contrôlent plus rien pour lui.
        assertThat(jdbc.queryForObject("SELECT desactive_le IS NOT NULL FROM users WHERE id = ?", Boolean.class, agentId))
            .isTrue();
        // Une suspension est définitive dans l'application.
        mvc.perform(post("/api/admin/verifications/{id}/suspension", o.id()).with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Encore\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void suspendre_un_evenement_l_annule_avec_un_motif_lu_par_son_seul_organisateur() throws Exception {
        Orga o = organisateur();
        organisateurVerifie(o.id());
        Event e = brouillon(o.id());
        eventService.publier(e.getId(), o.id());

        mvc.perform(post("/api/admin/evenements/{id}/suspension", e.getId()).with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Salle non reservee\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANNULE"));
        mvc.perform(get("/api/events/{id}", e.getId()).with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(jsonPath("$.suspenduMotif").value("Salle non reservee"));
        mvc.perform(get("/api/events/{id}", e.getId()))
            .andExpect(jsonPath("$.status").value("ANNULE"))
            .andExpect(jsonPath("$.suspenduMotif").doesNotExist());
        mvc.perform(post("/api/admin/evenements/{id}/suspension", e.getId()).with(connecte(o.id(), "ORGANISATEUR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"x\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void un_virement_attend_le_lendemain_un_scan_ou_une_derogation_et_un_compte_verifie() throws Exception {
        Orga o = organisateur();
        organisateurVerifie(o.id());
        Event e = brouillon(o.id());
        eventService.publier(e.getId(), o.id());
        Instant maintenant = Instant.now();
        Order commande = Order.creer(e.getId(), NumeroTelephone.depuis("77 123 45 67"),
            List.of(new OrderLine(UUID.randomUUID(), 1, Money.ofFcfa(10_000))), maintenant, Duration.ofMinutes(4),
            TauxCommission.CINQ_POUR_CENT).commande();
        commande.marquerPayee(maintenant.plusSeconds(30));
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> orderRepository.save(commande));
        String sansDerogation = "{\"eventId\":\"" + e.getId() + "\",\"montantFcfa\":9500,\"reference\":\"WAVE-"
            + UUID.randomUUID().toString().substring(0, 8) + "\",\"reverseLe\":\"" + LocalDate.now() + "\"}";

        // Événement à venir : trop tôt.
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(sansDerogation))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Reversement possible à partir du")));

        jdbc.update("UPDATE events SET debute_le = now() - interval '3 days' WHERE id = ?", e.getId());
        // Vente payée aujourd'hui : le virement attend encore demain (relecture de sécurité).
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(sansDerogation))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Reversement possible à partir du")));
        jdbc.update("UPDATE orders SET payee_le = now() - interval '3 days' WHERE event_id = ?", e.getId());
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(sansDerogation))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Aucun billet scanné")));

        // La liste montre le numéro vérifié vers lequel virer.
        mvc.perform(get("/api/admin/reversements").with(connecte(admin, "ADMIN")))
            .andExpect(jsonPath("$[?(@.eventId == '" + e.getId() + "')].numeroReversement").value("+221770000000"))
            .andExpect(jsonPath("$[?(@.eventId == '" + e.getId() + "')].billetsScannes").value(0));

        String avecDerogation = sansDerogation.replace("}", ",\"derogationScans\":\"Entree libre sans controle\"}");
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(avecDerogation))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.derogationScans").value("Entree libre sans controle"));
        assertThat(jdbc.queryForObject("SELECT dernier_reversement_le IS NOT NULL FROM verifications_organisateurs "
            + "WHERE organisateur_id = ?", Boolean.class, o.id())).isTrue();

        // Compte repassé en revue : plus aucun virement positif.
        jdbc.update("UPDATE verifications_organisateurs SET statut = 'EN_REVUE' WHERE organisateur_id = ?", o.id());
        mvc.perform(post("/api/admin/reversements").with(connecte(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(avecDerogation.replace("WAVE-", "WAVE-B-")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Organisateur non vérifié")));
    }

    @Test
    void chacun_ses_routes() throws Exception {
        Orga o = organisateur();
        mvc.perform(get("/api/verification")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/verification").with(connecte(admin, "ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/verifications").with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/verifications/{id}/pieces/IDENTITE", o.id()).with(connecte(o.id(), "ORGANISATEUR")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/verifications/{id}/pieces/IDENTITE", o.id()).with(connecte(admin, "ADMIN")))
            .andExpect(status().isNotFound());
        // Un fichier trop lourd est refusé avant le contrôleur.
        byte[] lourd = Arrays.copyOf(PDF, 5 * 1024 * 1024 + 1);
        deposer(o.id(), "IDENTITE", lourd, "gros.pdf")
            .andExpect(status().isBadRequest());
    }
}
