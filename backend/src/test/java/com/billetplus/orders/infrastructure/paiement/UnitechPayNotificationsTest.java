package com.billetplus.orders.infrastructure.paiement;

import com.billetplus.orders.application.port.NotificationPaiement;
import com.billetplus.orders.application.port.SignatureWebhookInvalideException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Signature « méthode 2 » de la documentation UnitechPay, recalculée ici de façon
 * indépendante (même algorithme que le code PHP de référence).
 */
class UnitechPayNotificationsTest {

    private static final String CLE = "cle-api-de-test";
    private final UnitechPayNotifications lecteur = new UnitechPayNotifications(new ObjectMapper(), CLE);

    static String signer(String cle, String event, String reference, String amount, String status, String signedAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String chaine = event + "|" + reference + "|" + amount + "|" + status + "|" + signedAt;
            return HexFormat.of().formatHex(mac.doFinal(chaine.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Corps au format de l'exemple de la documentation, signé avec {@code cle}. */
    static String webhook(String cle, String event, String reference, String amountJson, String status) {
        String signature = signer(cle, event, reference, amountJson.replace(".0", ""), status, "1716540300");
        return """
            {"event": "%s", "transaction_id": 18, "reference": "%s", "amount": %s, "status": "%s",
             "method": "wave", "commission": 75, "net_amount": 4925, "timestamp": 1716540300,
             "signed_at": 1716540300, "signature": "%s"}""".formatted(event, reference, amountJson, status, signature);
    }

    @Test
    void un_paiement_signe_est_lu_avec_ses_seuls_champs_signes() {
        NotificationPaiement lue = lecteur.lire(webhook(CLE, "payment_completed", "wave_66a1b2_1716540000", "5000", "completed"));

        assertThat(lue.type()).isEqualTo(NotificationPaiement.Type.PAYE);
        assertThat(lue.reference()).isEqualTo("wave_66a1b2_1716540000");
        assertThat(lue.montant()).isEqualTo(5000);
    }

    @Test
    void une_signature_fausse_ou_faite_avec_une_autre_cle_est_refusee() {
        assertThatThrownBy(() -> lecteur.lire(webhook("autre-cle", "payment_completed", "wave_1", "5000", "completed")))
            .isInstanceOf(SignatureWebhookInvalideException.class);
        String sansSignature = "{\"event\":\"payment_completed\",\"reference\":\"wave_1\",\"amount\":5000,"
            + "\"status\":\"completed\",\"signed_at\":1716540300}";
        assertThatThrownBy(() -> lecteur.lire(sansSignature)).isInstanceOf(SignatureWebhookInvalideException.class);
    }

    @Test
    void modifier_un_champ_signe_casse_la_signature() {
        String original = webhook(CLE, "payment_completed", "wave_1", "5000", "completed");

        assertThatThrownBy(() -> lecteur.lire(original.replace("\"amount\": 5000", "\"amount\": 50")))
            .isInstanceOf(SignatureWebhookInvalideException.class);
        assertThatThrownBy(() -> lecteur.lire(original.replace("\"reference\": \"wave_1\"", "\"reference\": \"wave_2\"")))
            .isInstanceOf(SignatureWebhookInvalideException.class);
        assertThatThrownBy(() -> lecteur.lire(original.replace("payment_completed", "payment_failed")))
            .isInstanceOf(SignatureWebhookInvalideException.class);
    }

    @Test
    void un_champ_non_signe_peut_changer_sans_effet_sur_ce_qui_est_lu() {
        // transaction_id et method ne sont pas signés (ADR 0007) : ils ne servent jamais à décider.
        String modifie = webhook(CLE, "payment_completed", "wave_1", "5000", "completed")
            .replace("\"transaction_id\": 18", "\"transaction_id\": 999")
            .replace("\"method\": \"wave\"", "\"method\": \"orange\"");

        assertThat(lecteur.lire(modifie)).isEqualTo(new NotificationPaiement(NotificationPaiement.Type.PAYE, "wave_1", 5000));
    }

    @Test
    void un_montant_ecrit_avec_decimale_est_signe_comme_php_l_ecrit() {
        // json_decode PHP donne 5000.0, concaténé en « 5000 ».
        NotificationPaiement lue = lecteur.lire(webhook(CLE, "payment_completed", "wave_1", "5000.0", "completed"));
        assertThat(lue.montant()).isEqualTo(5000);
    }

    @Test
    void une_signature_en_majuscules_est_acceptee() {
        String corps = webhook(CLE, "payment_completed", "wave_1", "5000", "completed");
        String signature = signer(CLE, "payment_completed", "wave_1", "5000", "completed", "1716540300");
        assertThat(lecteur.lire(corps.replace(signature, signature.toUpperCase(Locale.ROOT))).type())
            .isEqualTo(NotificationPaiement.Type.PAYE);
    }

    @Test
    void seul_un_evenement_completed_avec_le_statut_completed_est_un_paiement() {
        assertThat(lecteur.lire(webhook(CLE, "payment_completed", "wave_1", "5000", "pending")).type())
            .isEqualTo(NotificationPaiement.Type.AUTRE);
        assertThat(lecteur.lire(webhook(CLE, "payment_failed", "wave_1", "5000", "failed")).type())
            .isEqualTo(NotificationPaiement.Type.ECHOUE);
        assertThat(lecteur.lire(webhook(CLE, "payment_expired", "wave_1", "5000", "expired")).type())
            .isEqualTo(NotificationPaiement.Type.ECHOUE);
        assertThat(lecteur.lire(webhook(CLE, "withdrawal_processed", "wd_1", "5000", "processed")).type())
            .isEqualTo(NotificationPaiement.Type.AUTRE);
    }

    @Test
    void un_corps_illisible_ou_sans_reference_est_refuse() {
        assertThatThrownBy(() -> lecteur.lire("pas du json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lecteur.lire("[1,2]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lecteur.lire(webhook(CLE, "payment_completed", "", "5000", "completed")))
            .isInstanceOf(IllegalArgumentException.class);
        // Montant non numérique, correctement signé : la signature passe, le montant est refusé.
        String montantTexte = "{\"event\":\"payment_completed\",\"reference\":\"wave_1\",\"amount\":\"abc\","
            + "\"status\":\"completed\",\"signed_at\":1716540300,\"signature\":\""
            + signer(CLE, "payment_completed", "wave_1", "abc", "completed", "1716540300") + "\"}";
        assertThatThrownBy(() -> lecteur.lire(montantTexte)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sans_cle_configuree_tout_webhook_est_refuse() {
        UnitechPayNotifications sansCle = new UnitechPayNotifications(new ObjectMapper(), "");
        // Webhook correctement signé avec la vraie clé : sans clé configurée, il reste refusé.
        assertThatThrownBy(() -> sansCle.lire(webhook(CLE, "payment_completed", "wave_1", "5000", "completed")))
            .isInstanceOf(SignatureWebhookInvalideException.class);
    }
}
