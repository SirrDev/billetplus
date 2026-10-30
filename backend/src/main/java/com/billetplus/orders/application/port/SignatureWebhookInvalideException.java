package com.billetplus.orders.application.port;

/**
 * Webhook dont la signature est absente ou fausse : rien n'est traité, réponse
 * 401. Message générique, sans la signature attendue.
 */
public class SignatureWebhookInvalideException extends RuntimeException {

    public SignatureWebhookInvalideException() {
        super("Signature invalide");
    }
}
