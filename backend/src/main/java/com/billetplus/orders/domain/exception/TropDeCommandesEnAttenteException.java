package com.billetplus.orders.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

/**
 * Le numéro de téléphone a déjà trop de commandes en attente de paiement pour
 * cet événement (ADR 0006 : limite par téléphone, faute de compte). Sans cette
 * limite, des commandes jamais payées bloqueraient les places de tout un
 * événement. Traduite en HTTP 409, message générique.
 */
public class TropDeCommandesEnAttenteException extends BusinessConflictException {

    public TropDeCommandesEnAttenteException() {
        super("Trop de commandes en attente de paiement pour ce numéro : payez ou attendez leur expiration");
    }
}
