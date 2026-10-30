package com.billetplus.orders.application.port;

/**
 * Notification de la passerelle, après vérification de sa signature, réduite aux
 * seuls champs SIGNÉS (ADR 0007) : ce sont les seuls sur lesquels on décide.
 *
 * @param reference référence du paiement chez la passerelle (clé de rapprochement)
 * @param montant   montant payé, en FCFA
 */
public record NotificationPaiement(Type type, String reference, long montant) {

    public enum Type {
        /** Paiement réussi. */
        PAYE,
        /** Paiement échoué ou expiré : échec définitif. */
        ECHOUE,
        /** Tout autre événement (retraits...) : sans effet sur les commandes. */
        AUTRE
    }
}
