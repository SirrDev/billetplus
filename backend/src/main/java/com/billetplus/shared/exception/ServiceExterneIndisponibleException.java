package com.billetplus.shared.exception;

/**
 * Un service externe (passerelle de paiement...) est injoignable, en erreur ou
 * a renvoyé une réponse inexploitable. Traduite en HTTP 503 avec un message
 * générique : la cause détaillée va dans le journal, jamais au client.
 *
 * Type purement technique, sans règle métier (ADR 0001).
 */
public class ServiceExterneIndisponibleException extends RuntimeException {

    public ServiceExterneIndisponibleException(String message) {
        super(message);
    }

    public ServiceExterneIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
