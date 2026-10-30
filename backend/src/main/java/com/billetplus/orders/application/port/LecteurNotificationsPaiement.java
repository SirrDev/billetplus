package com.billetplus.orders.application.port;

/**
 * Port (niveau 3, voir ADR 0002) — lit et authentifie le corps brut d'un webhook
 * de la passerelle. L'implémentation (signature UnitechPay) vit dans
 * infrastructure/paiement/.
 */
public interface LecteurNotificationsPaiement {

    /**
     * @throws SignatureWebhookInvalideException (401) signature absente ou fausse
     * @throws IllegalArgumentException          (400) corps illisible ou champ signé manquant
     */
    NotificationPaiement lire(String corpsBrut);
}
