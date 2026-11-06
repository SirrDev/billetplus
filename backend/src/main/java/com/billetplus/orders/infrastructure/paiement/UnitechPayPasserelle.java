package com.billetplus.orders.infrastructure.paiement;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.exception.PaiementRefuseException;
import com.billetplus.shared.exception.ServiceExterneIndisponibleException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur HTTP de la passerelle UnitechPay (ADR 0007, API v1.2.0) :
 * POST https://api.unitech.sn/api.php?action=create_wave_payment | create_orange_om,
 * clé d'API en Authorization: Bearer, montant en XOF, customer_number sur 9 chiffres.
 *
 * Réponses :
 * - 401 ou 403 : notre clé est refusée → 503 et ERROR dans le journal (à corriger côté configuration) ;
 * - autre 4xx : demande refusée (numéro, moyen) → {@link PaiementRefuseException} (409) ;
 * - 5xx, délai dépassé, réponse incohérente → {@link ServiceExterneIndisponibleException} (503).
 * Une réponse « réussie » est contrôlée : succès explicite, référence présente,
 * lien https, et MÊME montant que celui demandé.
 *
 * La clé d'API n'est jamais journalisée, ni le corps complet des réponses : seuls
 * le code HTTP et le message d'erreur de la passerelle (tronqué) le sont.
 */
@Component
class UnitechPayPasserelle implements PasserellePaiement {

    private static final Logger LOG = LoggerFactory.getLogger(UnitechPayPasserelle.class);
    private static final Duration DELAI_CONNEXION = Duration.ofSeconds(5);
    private static final Duration DELAI_LECTURE = Duration.ofSeconds(15);
    private static final int LONGUEUR_MAX_MESSAGE_JOURNAL = 200;

    private final RestClient client;
    private final String cleApi;
    private final String urlRetourSucces;
    private final String urlRetourAnnulation;
    private final String urlRetourRecharge;

    @Autowired
    UnitechPayPasserelle(RestClient.Builder builder,
                         @Value("${billetplus.paiement.unitechpay.url-api:https://api.unitech.sn}") String urlApi,
                         @Value("${billetplus.paiement.unitechpay.cle-api:}") String cleApi,
                         @Value("${billetplus.paiement.unitechpay.url-retour-succes:}") String urlRetourSucces,
                         @Value("${billetplus.paiement.unitechpay.url-retour-annulation:}") String urlRetourAnnulation,
                         @Value("${billetplus.paiement.unitechpay.url-retour-recharge:}") String urlRetourRecharge) {
        this(builder.baseUrl(urlApi).requestFactory(fabriqueAvecDelais()).build(),
            cleApi, urlRetourSucces, urlRetourAnnulation, urlRetourRecharge);
    }

    /** Pour les tests : un RestClient déjà construit (lié à un faux serveur). */
    UnitechPayPasserelle(RestClient client, String cleApi, String urlRetourSucces, String urlRetourAnnulation) {
        this(client, cleApi, urlRetourSucces, urlRetourAnnulation, "");
    }

    /** Pour les tests : avec le lien de retour d'une recharge (ADR 0010). */
    UnitechPayPasserelle(RestClient client, String cleApi, String urlRetourSucces, String urlRetourAnnulation,
                         String urlRetourRecharge) {
        this.client = client;
        this.cleApi = cleApi;
        this.urlRetourSucces = urlRetourSucces;
        this.urlRetourAnnulation = urlRetourAnnulation;
        this.urlRetourRecharge = urlRetourRecharge;
    }

    private static JdkClientHttpRequestFactory fabriqueAvecDelais() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(DELAI_CONNEXION).build();
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(http);
        fabrique.setReadTimeout(DELAI_LECTURE);
        return fabrique;
    }

    @Override
    public PaiementCree ouvrir(DemandePaiement demande) {
        if (cleApi == null || cleApi.isBlank()) {
            throw new ServiceExterneIndisponibleException("Paiement non configuré : UNITECHPAY_API_KEY absente");
        }
        long montant = demande.montant().valeur().longValueExact();
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("amount", montant);
        // Format des exemples de la documentation : 9 chiffres, sans indicatif (771234567).
        corps.put("customer_number", demande.telephone().valeur().substring("+221".length()));
        corps.put("description", demande.description());
        if (demande.retour() == Retour.RECHARGE) {
            // Retour vers l'écran du crédit, dans les deux cas : il affiche l'état de la recharge.
            ajouterSiPresent(corps, "callback_success", lien(urlRetourRecharge, demande.orderId()));
            ajouterSiPresent(corps, "callback_cancel", lien(urlRetourRecharge, demande.orderId()));
        } else {
            ajouterSiPresent(corps, "callback_success", lien(urlRetourSucces, demande.orderId()));
            ajouterSiPresent(corps, "callback_cancel", lien(urlRetourAnnulation, demande.orderId()));
        }

        JsonNode reponse;
        try {
            reponse = client.post()
                .uri(uri -> uri.path("/api.php").queryParam("action", action(demande.moyen())).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cleApi)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(corps)
                .retrieve()
                .body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            throw traduire(e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ServiceExterneIndisponibleException("UnitechPay injoignable ou réponse illisible", e);
        }
        return lire(reponse, montant);
    }

    private static String action(MoyenPaiement moyen) {
        return switch (moyen) {
            case WAVE -> "create_wave_payment";
            case ORANGE_MONEY -> "create_orange_om";
        };
    }

    /** {orderId} dans l'URL de retour configurée est remplacé par l'identifiant de la commande. */
    private static String lien(String modele, UUID orderId) {
        return modele == null || modele.isBlank() ? null : modele.replace("{orderId}", orderId.toString());
    }

    private static void ajouterSiPresent(Map<String, Object> corps, String cle, String valeur) {
        if (valeur != null) {
            corps.put(cle, valeur);
        }
    }

    private RuntimeException traduire(HttpStatusCode statut, String corps, Exception cause) {
        String message = tronquer(corps);
        if (statut.value() == 401 || statut.value() == 403) {
            LOG.error("UnitechPay refuse notre clé d'API ({}) : vérifier UNITECHPAY_API_KEY. Réponse : {}",
                statut.value(), message);
            return new ServiceExterneIndisponibleException("Clé d'API UnitechPay refusée", cause);
        }
        if (statut.is4xxClientError()) {
            LOG.warn("UnitechPay refuse la demande de paiement ({}) : {}", statut.value(), message);
            return new PaiementRefuseException();
        }
        return new ServiceExterneIndisponibleException("UnitechPay en erreur (" + statut.value() + ") : " + message, cause);
    }

    private static PaiementCree lire(JsonNode reponse, long montantDemande) {
        if (reponse == null || !reponse.path("success").asBoolean(false)) {
            String message = reponse == null ? "réponse vide" : tronquer(reponse.path("message").asText(""));
            // Un refus explicite (success: false) avec un code HTTP 200 : traité comme un refus de la demande.
            LOG.warn("UnitechPay n'a pas ouvert le paiement : {}", message);
            throw new PaiementRefuseException();
        }
        JsonNode data = reponse.path("data");
        String reference = data.path("reference").asText("");
        String url = data.path("payment_url").asText("");
        JsonNode transaction = data.path("transaction_id");
        JsonNode montant = data.path("amount");
        if (reference.isBlank() || reference.length() > 100 || !url.startsWith("https://")
            || !transaction.canConvertToLong() || !montant.isNumber() || montant.asDouble() != montantDemande) {
            throw new ServiceExterneIndisponibleException(
                "Réponse UnitechPay incohérente (référence, lien, transaction ou montant)");
        }
        return new PaiementCree(reference, transaction.asLong(), url);
    }

    /**
     * Message de la passerelle prêt à journaliser : toute suite de 9 chiffres ou plus (un
     * numéro de mobile recopié dans un message d'erreur, par exemple) est masquée, puis le
     * texte est tronqué. Les journaux partent souvent vers un outil tiers.
     */
    static String tronquer(String texte) {
        if (texte == null) {
            return "";
        }
        String masque = texte.replaceAll("\\d{9,}", "•••");
        return masque.length() <= LONGUEUR_MAX_MESSAGE_JOURNAL ? masque : masque.substring(0, LONGUEUR_MAX_MESSAGE_JOURNAL) + "…";
    }
}
