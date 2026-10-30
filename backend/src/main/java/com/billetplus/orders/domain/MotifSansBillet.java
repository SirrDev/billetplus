package com.billetplus.orders.domain;

/** Pourquoi une commande payée n'a pas pu recevoir ses billets : à rembourser (ADR 0006). */
public enum MotifSansBillet {
    /** Paiement reçu après l'expiration de la réservation des places. */
    RESERVATION_EXPIREE,
    /** L'événement a été annulé ou n'est plus en vente entre la commande et le paiement. */
    VENTE_FERMEE,
    /** Le quota de la catégorie était épuisé au moment de l'émission (risque résiduel). */
    PLACES_INDISPONIBLES,
    /** La passerelle signale un montant payé différent de celui de la commande. */
    MONTANT_INCORRECT,
    /**
     * Paiement reçu pour une commande déjà annulée : par exemple un webhook « payé »
     * arrivé après un « expiré » (l'ordre d'arrivée des webhooks n'est pas garanti).
     */
    PAIEMENT_APRES_ANNULATION
}
