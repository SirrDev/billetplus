package com.billetplus.orders.application.port;

import com.billetplus.orders.domain.MouvementCredit;
import com.billetplus.shared.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — crédit prépayé des organisateurs (ADR 0010) : un
 * solde jamais négatif et un journal des mouvements, jamais modifié. Chaque écriture
 * change le solde ET ajoute sa ligne au journal, dans la transaction de l'appelant.
 */
public interface CreditRepository {

    /**
     * Débite le crédit par une seule requête conditionnelle (solde ≥ montant) : deux
     * débits simultanés ne dépensent jamais le même franc (PostgreSQL verrouille la ligne
     * jusqu'à la fin de la transaction). Exige une transaction ouverte par l'appelant.
     *
     * @return faux si le crédit est insuffisant (ou inexistant) : rien n'est écrit
     */
    boolean debiterEntreeLibre(UUID organisateurId, Money montant, UUID orderId, Instant maintenant);

    /**
     * Crédite le solde d'une recharge payée (crée le solde au premier crédit). Exige une
     * transaction ouverte par l'appelant. Une recharge ne crédite qu'une fois (index unique
     * du journal) : un second crédit pour la même recharge lève une exception.
     */
    void crediterRecharge(UUID organisateurId, Money montant, UUID rechargeId, Instant maintenant);

    /**
     * Sérialise les entrées libres d'un même numéro pour un même événement jusqu'à la fin
     * de la transaction de l'appelant : sans ce verrou, deux demandes simultanées liraient
     * le même nombre de billets déjà obtenus et dépasseraient ensemble le plafond, aux
     * frais de l'organisateur. Exige une transaction ouverte par l'appelant.
     */
    void verrouillerEntreesLibres(String telephone, UUID eventId);

    /** Solde de l'organisateur ; zéro s'il n'a jamais rechargé. */
    Money solde(UUID organisateurId);

    /** Derniers mouvements, les plus récents d'abord. */
    List<MouvementCredit> derniersMouvements(UUID organisateurId, int limite);
}
