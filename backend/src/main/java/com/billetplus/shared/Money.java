package com.billetplus.shared;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Value object représentant un montant en FCFA (pas de sous-unité décimale).
 *
 * Fait partie du "shared kernel" : utilisable par tous les modules, mais ne
 * contient aucune règle métier propre à un module (pas de logique de commission,
 * de remboursement, etc. — cela reste dans les modules orders/tickets concernés).
 * Voir docs/architecture/adr/0001-monolithe-modulaire.md.
 */
public final class Money {

    private final BigInteger montantFcfa;

    private Money(BigInteger montantFcfa) {
        if (montantFcfa.signum() < 0) {
            throw new IllegalArgumentException("Un montant ne peut pas être négatif");
        }
        this.montantFcfa = montantFcfa;
    }

    public static Money ofFcfa(long montant) {
        return new Money(BigInteger.valueOf(montant));
    }

    public Money plus(Money autre) {
        return new Money(this.montantFcfa.add(autre.montantFcfa));
    }

    public Money moins(Money autre) {
        return new Money(this.montantFcfa.subtract(autre.montantFcfa));
    }

    /** Montant multiplié par une quantité (par exemple prix unitaire × nombre de places). */
    public Money fois(int quantite) {
        if (quantite < 0) {
            throw new IllegalArgumentException("La quantité ne peut pas être négative");
        }
        return new Money(montantFcfa.multiply(BigInteger.valueOf(quantite)));
    }

    public BigInteger valeur() {
        return montantFcfa;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return montantFcfa.equals(money.montantFcfa);
    }

    @Override
    public int hashCode() {
        return Objects.hash(montantFcfa);
    }

    @Override
    public String toString() {
        return montantFcfa + " FCFA";
    }
}
