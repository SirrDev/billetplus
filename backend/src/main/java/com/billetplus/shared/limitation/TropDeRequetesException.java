package com.billetplus.shared.limitation;

/**
 * Limite de débit atteinte. Traduite en HTTP 429 avec l'en-tête Retry-After
 * par GlobalExceptionHandler.
 */
public class TropDeRequetesException extends RuntimeException {

    private final long reessayerDansSecondes;

    public TropDeRequetesException(long reessayerDansSecondes) {
        super("Trop de requêtes : réessayez dans " + reessayerDansSecondes + " s");
        this.reessayerDansSecondes = reessayerDansSecondes;
    }

    public long getReessayerDansSecondes() {
        return reessayerDansSecondes;
    }
}
