package com.billetplus;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.exception.PaiementRefuseException;
import com.billetplus.shared.exception.ServiceExterneIndisponibleException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/orders/{id}/paiement sur la vraie pile. Seule la passerelle est
 * simulée (@MockBean de ContexteCompletTestBase, ADR 0007 : pas d'environnement
 * de test chez UnitechPay).
 */
class PaiementHttpIntegrationTest extends ContexteCompletTestBase {

    private static final String JETON = "X-Jeton-Commande";

    @Autowired
    private JdbcTemplate jdbc;

    /** Une commande créée par l'API, sur un événement publié : renvoie {id, jeton}. */
    private String[] commande() throws Exception {
        UUID lieu = UUID.randomUUID();
        jdbc.update("INSERT INTO venues (id, nom, adresse, ville_code) VALUES (?, 'Arena', 'Dakar', 'SN-DAKAR')", lieu);
        UUID evenement = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, titre, debute_le, venue_id, organizer_id, status) VALUES (?, ?, ?, ?, ?, 'PUBLIE')",
            evenement, "Concert " + evenement, Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS)), lieu, UUID.randomUUID());
        UUID categorie = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_categories (id, event_id, nom, prix_fcfa, quota_total) VALUES (?, ?, 'Standard', 5000, 50)",
            categorie, evenement);
        String telephone = "77" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000);
        String reponse = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + evenement + "\",\"telephone\":\"" + telephone
                    + "\",\"lignes\":[{\"categoryId\":\"" + categorie + "\",\"quantite\":2}]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(reponse, "$.commande.id"), JsonPath.read(reponse, "$.jetonAcces")};
    }

    private ResultActions payer(String id, String jeton, String moyen) throws Exception {
        return mvc.perform(post("/api/orders/" + id + "/paiement").header(JETON, jeton)
            .contentType(MediaType.APPLICATION_JSON).content("{\"moyen\":\"" + moyen + "\"}"));
    }

    private void passerelleRepond(String reference) {
        when(passerellePaiement.ouvrir(any())).thenReturn(
            new PasserellePaiement.PaiementCree(reference, 18, "https://pay.wave.com/c/" + reference));
    }

    @Test
    void l_acheteur_obtient_le_lien_de_paiement_et_le_meme_lien_s_il_redemande() throws Exception {
        String[] commande = commande();
        String reference = "wave_" + UUID.randomUUID();
        passerelleRepond(reference);

        payer(commande[0], commande[1], "WAVE")
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.moyen").value("WAVE"))
            .andExpect(jsonPath("$.urlPaiement").value("https://pay.wave.com/c/" + reference))
            .andExpect(jsonPath("$.expireLe").exists());
        payer(commande[0], commande[1], "WAVE")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.urlPaiement").value("https://pay.wave.com/c/" + reference));

        // Un seul paiement ouvert chez la passerelle, malgré deux demandes.
        verify(passerellePaiement, times(1)).ouvrir(any());
        String enBase = jdbc.queryForObject("SELECT paiement_reference FROM orders WHERE id = ?::uuid",
            String.class, commande[0]);
        org.assertj.core.api.Assertions.assertThat(enBase).isEqualTo(reference);
        // La lecture de la commande reste possible, toujours sans le jeton en réponse.
        mvc.perform(get("/api/orders/" + commande[0]).header(JETON, commande[1])).andExpect(status().isOk());
    }

    @Test
    void un_autre_moyen_apres_un_paiement_ouvert_repond_409() throws Exception {
        String[] commande = commande();
        passerelleRepond("wave_" + UUID.randomUUID());
        payer(commande[0], commande[1], "WAVE").andExpect(status().isOk());

        payer(commande[0], commande[1], "ORANGE_MONEY").andExpect(status().isConflict());
    }

    @Test
    void sans_le_bon_jeton_la_commande_est_introuvable() throws Exception {
        String[] commande = commande();

        payer(commande[0], "faux", "WAVE").andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + commande[0] + "/paiement").contentType(MediaType.APPLICATION_JSON)
                .content("{\"moyen\":\"WAVE\"}"))
            .andExpect(status().isNotFound());
        verify(passerellePaiement, times(0)).ouvrir(any());
    }

    @Test
    void un_moyen_inconnu_ou_absent_repond_400() throws Exception {
        String[] commande = commande();
        payer(commande[0], commande[1], "CARTE").andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/" + commande[0] + "/paiement").header(JETON, commande[1])
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void une_panne_de_la_passerelle_repond_503_et_un_refus_409_avec_des_messages_generiques() throws Exception {
        String[] commande = commande();
        when(passerellePaiement.ouvrir(any())).thenThrow(new ServiceExterneIndisponibleException("détail interne"));
        payer(commande[0], commande[1], "WAVE")
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("détail interne"))));

        // doThrow et non when(...) : when appellerait le mock, qui lève déjà l'exception précédente.
        doThrow(new PaiementRefuseException()).when(passerellePaiement).ouvrir(any());
        payer(commande[0], commande[1], MoyenPaiement.ORANGE_MONEY.name()).andExpect(status().isConflict());

        // Aucune des deux tentatives n'a laissé de paiement : une troisième peut réussir.
        String reference = "om_" + UUID.randomUUID();
        doReturn(new PasserellePaiement.PaiementCree(reference, 19, "https://om.example/p/" + reference))
            .when(passerellePaiement).ouvrir(any());
        payer(commande[0], commande[1], "ORANGE_MONEY").andExpect(status().isOk());
    }
}
