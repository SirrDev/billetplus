package com.billetplus.orders.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

/**
 * La passerelle a refusé d'ouvrir le paiement (numéro non inscrit chez
 * l'opérateur, moyen indisponible...). Traduite en HTTP 409. Message générique :
 * celui de la passerelle n'est pas relayé tel quel à l'acheteur.
 */
public class PaiementRefuseException extends BusinessConflictException {

    public PaiementRefuseException() {
        super("Le paiement n'a pas pu être ouvert : vérifiez le numéro et le moyen de paiement choisis");
    }
}
