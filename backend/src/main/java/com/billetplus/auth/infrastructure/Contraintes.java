package com.billetplus.auth.infrastructure;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Reconnaît une contrainte violée par son NOM, jamais par un texte cherché dans le message
 * (le détail PostgreSQL recopie les valeurs saisies : HANDOFF, section 10, n° 72).
 */
final class Contraintes {

    private Contraintes() {
    }

    static boolean violee(Throwable erreur, String contrainte) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return contrainte.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
