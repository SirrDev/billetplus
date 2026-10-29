package com.billetplus.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Traduction centralisée des exceptions en réponses HTTP. Pur détail
 * d'infrastructure transverse — aucune règle métier ici, cohérent avec
 * la discipline shared/ posée dans ADR 0001.
 *
 * Correspondances :
 * BadCredentials → 401, IllegalArgument → 400, NoSuchElement → 404,
 * BusinessConflict → 409, OptimisticLockingFailure → 409,
 * DataAccessResourceFailure → 503,
 * AccessDenied → 401 (anonyme) ou 403 (connecté), MethodArgumentNotValid → 400.
 *
 * Une IllegalStateException ordinaire n'a volontairement PAS de handler :
 * c'est un défaut interne, qui doit rester une erreur 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException e) {
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException e) {
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessConflict(BusinessConflictException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Conflit de version (@Version) : une autre requête a modifié la même
     * ressource entre la lecture et l'écriture (réservation de quota ou scan
     * concurrents). L'opération a été annulée et peut être retentée : c'est un
     * 409, pas une erreur serveur. Message générique : ni entité, ni identifiant,
     * ni version exposés.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException e) {
        return buildResponse(HttpStatus.CONFLICT,
                "La ressource a été modifiée par une autre requête, veuillez réessayer");
    }

    /**
     * Ressource technique injoignable : Redis (RedisConnectionFailureException,
     * réservations temporaires) ou base de données. La demande n'a pas été
     * traitée et peut être retentée : 503, pas 500. Les réservations refusent
     * volontairement de fonctionner sans Redis : se rabattre sur PostgreSQL seul
     * rouvrirait la vente sans réservation, donc des paiements sans billet.
     * Message générique : ni hôte, ni port, ni détail de connexion.
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Map<String, Object>> handleResourceFailure(DataAccessResourceFailureException e) {
        // Journalisé côté serveur (trace comprise) : sans cela, une panne Redis
        // ou PostgreSQL ne laisserait aucune trace d'exploitation. Le client ne
        // reçoit que le message générique.
        LOG.error("Ressource technique indisponible ({})", e.getClass().getSimpleName(), e);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "Service temporairement indisponible, veuillez réessayer");
    }

    /**
     * Un accès refusé à un visiteur non authentifié est un 401 (« identifie-toi »),
     * pas un 403 (« tu es identifié mais sans droit »). Un @PreAuthorize refusé
     * lève AuthorizationDeniedException, sous-classe d'AccessDeniedException :
     * ce handler la couvre aussi.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean anonyme = auth == null || auth instanceof AnonymousAuthenticationToken;
        return anonyme
                ? buildResponse(HttpStatus.UNAUTHORIZED, "Authentification requise")
                : buildResponse(HttpStatus.FORBIDDEN, "Accès refusé");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Requête invalide");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
