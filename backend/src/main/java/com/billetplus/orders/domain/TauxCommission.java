package com.billetplus.orders.domain;

import com.billetplus.shared.Money;

import java.math.BigInteger;

/**
 * Taux de commission de Billet+ (ADR 0009), en points de base : 500 = 5 %. Entier, pour
 * que l'argent ne passe jamais par un nombre à virgule.
 *
 * @param pointsBase de 0 (aucune commission) à 10 000 (100 %)
 */
public record TauxCommission(int pointsBase) {

    /** 100 % en points de base. */
    public static final int CENT_POUR_CENT = 10_000;

    /** 5 %, taux du cahier des charges. */
    public static final TauxCommission CINQ_POUR_CENT = new TauxCommission(500);

    public TauxCommission {
        if (pointsBase < 0 || pointsBase > CENT_POUR_CENT) {
            throw new IllegalArgumentException("Le taux de commission doit être compris entre 0 et 10 000 points de base");
        }
    }

    /**
     * Commission sur un montant, arrondie au franc inférieur : le franc d'écart revient à
     * l'organisateur. Exemple : 5 % de 3 333 F = 166 F (et non 166,65).
     */
    public Money commissionSur(Money montant) {
        BigInteger commission = montant.valeur()
            .multiply(BigInteger.valueOf(pointsBase))
            .divide(BigInteger.valueOf(CENT_POUR_CENT));
        return Money.ofFcfa(commission.longValueExact());
    }
}
