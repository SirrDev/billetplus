package com.billetplus.orders.domain;

/**
 * Cycle de vie d'une commande (ADR 0006).
 *
 * <pre>
 * EN_ATTENTE_PAIEMENT ──payée à temps──────────────► PAYEE
 *        │  └─────────payée, billets impossibles───► PAYEE_SANS_BILLET
 *        ├──délai dépassé──► EXPIREE ──payée tard──► PAYEE_SANS_BILLET
 *        └──annulée────────► ANNULEE
 * </pre>
 *
 * PAYEE_SANS_BILLET est un état final à rembourser à la main (décision B : pas de
 * remboursement automatique au MVP). Il ne doit jamais être perdu.
 */
public enum OrderStatus {
    EN_ATTENTE_PAIEMENT,
    PAYEE,
    PAYEE_SANS_BILLET,
    EXPIREE,
    ANNULEE
}
