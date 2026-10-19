package com.billetplus.shared.exception;

/**
 * Conflit avec l'état courant d'une ressource métier : transition de statut
 * interdite (publier deux fois), quota dépassé, billet déjà scanné, etc.
 * Traduite en HTTP 409 par {@link GlobalExceptionHandler}.
 *
 * Étend {@link IllegalStateException} volontairement : le code et les tests
 * existants qui attendent ce type continuent de fonctionner, et une
 * IllegalStateException ordinaire (ex. panne de signature Ed25519) reste une
 * erreur serveur (500), jamais un 409.
 *
 * Type purement technique : aucune règle métier ici, cohérent avec la
 * discipline shared/ posée dans ADR 0001.
 */
public class BusinessConflictException extends IllegalStateException {

    public BusinessConflictException(String message) {
        super(message);
    }
}
