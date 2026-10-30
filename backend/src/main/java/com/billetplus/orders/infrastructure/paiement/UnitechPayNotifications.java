package com.billetplus.orders.infrastructure.paiement;

import com.billetplus.orders.application.port.LecteurNotificationsPaiement;
import com.billetplus.orders.application.port.NotificationPaiement;
import com.billetplus.orders.application.port.SignatureWebhookInvalideException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Lecture et authentification d'un webhook UnitechPay (ADR 0007, API v1.2.0).
 *
 * Signature « méthode 2 » de la documentation : HMAC-SHA256, avec la clé d'API
 * comme secret, de la chaîne {@code event|reference|amount|status|signed_at},
 * comparée au champ {@code signature} du corps, en temps constant. Chaque champ
 * est reconstitué comme PHP le concatène (le code de référence de la passerelle) :
 * un nombre entier s'écrit sans décimale (5000, jamais 5000.0).
 *
 * Seuls les champs signés sortent d'ici (NotificationPaiement) : transaction_id,
 * method, commission... ne sont pas signés et ne servent jamais à décider.
 */
@Component
class UnitechPayNotifications implements LecteurNotificationsPaiement {

    private final ObjectMapper json;
    private final String cleApi;

    @Autowired
    UnitechPayNotifications(ObjectMapper json, @Value("${billetplus.paiement.unitechpay.cle-api:}") String cleApi) {
        this.json = json;
        this.cleApi = cleApi;
    }

    @Override
    public NotificationPaiement lire(String corpsBrut) {
        if (cleApi == null || cleApi.isBlank()) {
            // Sans clé, aucune signature n'est vérifiable : tout webhook est refusé.
            throw new SignatureWebhookInvalideException();
        }
        JsonNode corps;
        try {
            corps = json.readTree(corpsBrut);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Corps de webhook illisible");
        }
        if (corps == null || !corps.isObject()) {
            throw new IllegalArgumentException("Corps de webhook illisible");
        }
        String event = texte(corps, "event");
        String reference = texte(corps, "reference");
        String montant = texte(corps, "amount");
        String statut = texte(corps, "status");
        String signeLe = texte(corps, "signed_at");
        String signature = corps.path("signature").asText("");

        String attendue = hmac(event + "|" + reference + "|" + montant + "|" + statut + "|" + signeLe);
        if (!MessageDigest.isEqual(attendue.getBytes(StandardCharsets.US_ASCII),
                signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new SignatureWebhookInvalideException();
        }

        if (reference.isEmpty()) {
            throw new IllegalArgumentException("Référence absente du webhook");
        }
        return new NotificationPaiement(type(event, statut), reference, montantEnFcfa(montant));
    }

    /**
     * Valeur d'un champ telle que PHP la concatène : texte tel quel, entier sans
     * décimale, champ absent ou null → chaîne vide (comme {@code ?? ''}).
     */
    private static String texte(JsonNode corps, String champ) {
        JsonNode valeur = corps.get(champ);
        if (valeur == null || valeur.isNull()) {
            return "";
        }
        if (valeur.isNumber()) {
            BigDecimal nombre = valeur.decimalValue();
            return nombre.stripTrailingZeros().scale() <= 0
                ? nombre.toBigIntegerExact().toString()
                : nombre.toPlainString();
        }
        if (valeur.isBoolean()) {
            return valeur.booleanValue() ? "1" : "";
        }
        return valeur.asText();
    }

    private static long montantEnFcfa(String montant) {
        try {
            return new BigDecimal(montant).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Montant du webhook invalide");
        }
    }

    /** Un paiement n'est « reçu » que si l'événement ET le statut le disent. */
    private static NotificationPaiement.Type type(String event, String statut) {
        return switch (event) {
            case "payment_completed" -> "completed".equals(statut)
                ? NotificationPaiement.Type.PAYE : NotificationPaiement.Type.AUTRE;
            case "payment_failed", "payment_expired" -> NotificationPaiement.Type.ECHOUE;
            default -> NotificationPaiement.Type.AUTRE;
        };
    }

    private String hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cleApi.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }
}
