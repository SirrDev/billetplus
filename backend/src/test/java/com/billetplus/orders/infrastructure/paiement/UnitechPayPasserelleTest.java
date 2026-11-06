package com.billetplus.orders.infrastructure.paiement;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.exception.PaiementRefuseException;
import com.billetplus.shared.Money;
import com.billetplus.shared.exception.ServiceExterneIndisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * L'adaptateur contre un faux serveur UnitechPay : format de la requête (action,
 * clé, corps) et traduction de chaque réponse possible. Les réponses reprennent
 * les exemples de la documentation (ADR 0007).
 */
class UnitechPayPasserelleTest {

    private static final String CLE = "cle-de-test";
    private static final UUID COMMANDE = UUID.randomUUID();

    private MockRestServiceServer serveur;
    private UnitechPayPasserelle passerelle;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.unitech.sn");
        serveur = MockRestServiceServer.bindTo(builder).build();
        passerelle = new UnitechPayPasserelle(builder.build(), CLE,
            "https://billetplus.sn/commandes/{orderId}/merci", "https://billetplus.sn/commandes/{orderId}");
    }

    private static PasserellePaiement.DemandePaiement demande(MoyenPaiement moyen, long montant) {
        return new PasserellePaiement.DemandePaiement(COMMANDE, moyen, Money.ofFcfa(montant),
            NumeroTelephone.depuis("+221 77 123 45 67"), "Billet+ : commande de billets");
    }

    private static String reponse(String reference, long montant, String url) {
        return """
            {"success": true, "data": {"transaction_id": 18, "reference": "%s", "payment_url": "%s",
             "amount": %d, "status": "pending"}}""".formatted(reference, url, montant);
    }

    @Test
    void un_paiement_wave_est_demande_avec_la_cle_le_montant_le_numero_local_et_les_liens_de_retour() {
        serveur.expect(requestTo("https://api.unitech.sn/api.php?action=create_wave_payment"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + CLE))
            .andExpect(jsonPath("$.amount").value(5000))
            .andExpect(jsonPath("$.customer_number").value("771234567"))
            .andExpect(jsonPath("$.callback_success").value("https://billetplus.sn/commandes/" + COMMANDE + "/merci"))
            .andExpect(jsonPath("$.callback_cancel").value("https://billetplus.sn/commandes/" + COMMANDE))
            .andRespond(withSuccess(reponse("wave_66a1b2_1716540000", 5000, "https://pay.wave.com/c/abc"),
                MediaType.APPLICATION_JSON));

        PasserellePaiement.PaiementCree cree = passerelle.ouvrir(demande(MoyenPaiement.WAVE, 5000));

        assertThat(cree.reference()).isEqualTo("wave_66a1b2_1716540000");
        assertThat(cree.transactionId()).isEqualTo(18);
        assertThat(cree.url()).isEqualTo("https://pay.wave.com/c/abc");
        serveur.verify();
    }

    @Test
    void orange_money_utilise_l_action_create_orange_om() {
        serveur.expect(requestTo("https://api.unitech.sn/api.php?action=create_orange_om"))
            .andRespond(withSuccess(reponse("om_1", 15000, "https://om.example/p"), MediaType.APPLICATION_JSON));

        assertThat(passerelle.ouvrir(demande(MoyenPaiement.ORANGE_MONEY, 15000)).reference()).isEqualTo("om_1");
        serveur.verify();
    }

    @Test
    void sans_liens_de_retour_configures_ils_ne_sont_pas_envoyes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.unitech.sn");
        MockRestServiceServer autre = MockRestServiceServer.bindTo(builder).build();
        UnitechPayPasserelle sansLiens = new UnitechPayPasserelle(builder.build(), CLE, "", "");
        autre.expect(requestTo("https://api.unitech.sn/api.php?action=create_wave_payment"))
            .andExpect(jsonPath("$.callback_success").doesNotExist())
            .andExpect(jsonPath("$.callback_cancel").doesNotExist())
            .andRespond(withSuccess(reponse("wave_2", 5000, "https://pay.wave.com/c/x"), MediaType.APPLICATION_JSON));

        sansLiens.ouvrir(demande(MoyenPaiement.WAVE, 5000));
        autre.verify();
    }

    @Test
    void une_cle_refusee_donne_un_service_indisponible_et_non_un_refus_de_l_acheteur() {
        serveur.expect(requestTo("https://api.unitech.sn/api.php?action=create_wave_payment"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"success\":false,\"message\":\"Cle invalide\",\"code\":401}")
                .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> passerelle.ouvrir(demande(MoyenPaiement.WAVE, 5000)))
            .isInstanceOf(ServiceExterneIndisponibleException.class);
    }

    @Test
    void une_demande_refusee_en_4xx_ou_avec_success_false_donne_un_refus_generique() {
        serveur.expect(requestTo("https://api.unitech.sn/api.php?action=create_wave_payment"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"success\":false,\"message\":\"Numero invalide\",\"code\":400}")
                .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> passerelle.ouvrir(demande(MoyenPaiement.WAVE, 5000)))
            .isInstanceOf(PaiementRefuseException.class)
            .hasMessageNotContaining("Numero invalide");

        serveur.reset();
        serveur.expect(requestTo("https://api.unitech.sn/api.php?action=create_wave_payment"))
            .andRespond(withSuccess("{\"success\":false,\"message\":\"Refus\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> passerelle.ouvrir(demande(MoyenPaiement.WAVE, 5000)))
            .isInstanceOf(PaiementRefuseException.class);
    }

    @Test
    void une_erreur_serveur_ou_un_delai_depasse_donnent_un_service_indisponible() {
        serveur.expect(requestTo("https://api.unitech.sn/api.php?action=create_wave_payment"))
            .andRespond(withServerError());
        assertThatThrownBy(() -> passerelle.ouvrir(demande(MoyenPaiement.WAVE, 5000)))
            .isInstanceOf(ServiceExterneIndisponibleException.class);

        serveur.reset();
        serveur.expect(requestTo("https://api.unitech.sn/api.php?action=create_wave_payment"))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThatThrownBy(() -> passerelle.ouvrir(demande(MoyenPaiement.WAVE, 5000)))
            .isInstanceOf(ServiceExterneIndisponibleException.class);
    }

    @Test
    void une_reponse_incoherente_est_rejetee() {
        String[] incoherentes = {
            reponse("wave_3", 4000, "https://pay.wave.com/c/x"),          // montant différent
            reponse("wave_3", 5000, "http://pay.wave.com/c/x"),           // lien non https
            reponse("", 5000, "https://pay.wave.com/c/x"),                // référence vide
            "{\"success\": true, \"data\": {\"reference\": \"wave_3\", \"payment_url\": \"https://p\", \"amount\": 5000}}",
            "pas du json"
        };
        for (String corps : incoherentes) {
            serveur.reset();
            serveur.expect(requestTo("https://api.unitech.sn/api.php?action=create_wave_payment"))
                .andRespond(withSuccess(corps, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> passerelle.ouvrir(demande(MoyenPaiement.WAVE, 5000)))
                .as(corps)
                .isInstanceOf(ServiceExterneIndisponibleException.class);
        }
    }

    @Test
    void sans_cle_configuree_aucune_requete_n_est_envoyee() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.unitech.sn");
        MockRestServiceServer autre = MockRestServiceServer.bindTo(builder).build();
        UnitechPayPasserelle sansCle = new UnitechPayPasserelle(builder.build(), "", "", "");

        assertThatThrownBy(() -> sansCle.ouvrir(demande(MoyenPaiement.WAVE, 5000)))
            .isInstanceOf(ServiceExterneIndisponibleException.class)
            .hasMessageContaining("UNITECHPAY_API_KEY");
        autre.verify();
    }

    /** Un numéro recopié dans un message d'erreur de la passerelle ne part pas dans les journaux. */
    @Test
    void les_messages_journalises_masquent_les_numeros_et_sont_tronques() {
        assertThat(UnitechPayPasserelle.tronquer("Numero 771234567 invalide")).isEqualTo("Numero ••• invalide");
        assertThat(UnitechPayPasserelle.tronquer("+221771234567")).isEqualTo("+•••");
        assertThat(UnitechPayPasserelle.tronquer("montant 5000 refuse")).isEqualTo("montant 5000 refuse");
        assertThat(UnitechPayPasserelle.tronquer(null)).isEmpty();
        assertThat(UnitechPayPasserelle.tronquer("x".repeat(500))).endsWith("…").hasSizeLessThan(500);
    }
}
