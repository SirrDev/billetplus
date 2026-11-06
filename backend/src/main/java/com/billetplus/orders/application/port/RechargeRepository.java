package com.billetplus.orders.application.port;

import com.billetplus.orders.domain.RechargeCredit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — recharges du crédit des organisateurs (ADR 0010).
 * Verrouillage optimiste : {@link #findById}, la transition du domaine et {@link #save}
 * s'exécutent dans la MÊME transaction, celle du cas d'usage.
 */
public interface RechargeRepository {

    Optional<RechargeCredit> findById(UUID id);

    /** Exige une transaction ouverte par l'appelant. */
    RechargeCredit save(RechargeCredit recharge);

    /** Recharge dont le paiement porte cette référence de passerelle (unique, V17). */
    Optional<RechargeCredit> findByReferencePaiement(String reference);

    /** Dernières recharges de l'organisateur, les plus récentes d'abord. */
    List<RechargeCredit> dernieres(UUID organisateurId, int limite);
}
