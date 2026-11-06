package com.billetplus.orders.domain;

/**
 * Cycle de vie d'une recharge du crédit d'un organisateur (ADR 0010).
 *
 * <pre>
 * EN_ATTENTE ──montant signé égal──► PAYEE (solde crédité une fois)
 *     ├──────montant différent─────► ANOMALIE (rien crédité, à régulariser à la main)
 *     └──────échec ou expiration───► ECHOUEE
 * </pre>
 */
public enum StatutRecharge {
    EN_ATTENTE,
    PAYEE,
    ECHOUEE,
    ANOMALIE
}
