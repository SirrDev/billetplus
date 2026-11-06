package com.billetplus.orders.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — une ligne du journal du crédit d'un
 * organisateur (ADR 0010), jamais modifiée. Le solde égale toujours la somme du journal.
 *
 * @param montantFcfa positif pour une recharge, négatif pour une entrée libre
 * @param rechargeId  la recharge créditée (type RECHARGE), sinon null
 * @param orderId     la commande gratuite débitée (type ENTREE_LIBRE), sinon null
 */
public record MouvementCredit(UUID id, UUID organisateurId, long montantFcfa, Type type, UUID rechargeId,
                              UUID orderId, Instant creeLe) {

    public enum Type {
        RECHARGE,
        ENTREE_LIBRE
    }
}
