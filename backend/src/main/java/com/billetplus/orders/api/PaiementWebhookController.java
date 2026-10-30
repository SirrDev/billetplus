package com.billetplus.orders.api;

import com.billetplus.orders.application.port.LecteurNotificationsPaiement;
import com.billetplus.orders.application.port.NotificationPaiement;
import com.billetplus.orders.application.port.SignatureWebhookInvalideException;
import com.billetplus.orders.application.usecase.ConfirmerPaiementUseCase;
import com.billetplus.shared.exception.BusinessConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook de la passerelle UnitechPay (ADR 0007). Route publique (SecurityConfig) :
 * l'authentification est la signature HMAC du corps, vérifiée AVANT tout traitement.
 *
 * Réponses : 200 (traité, déjà traité ou sans effet), 401 (signature), 400 (corps
 * illisible ou trop grand), 503 (conflit passager : à relancer). Jamais 409 :
 * beaucoup de passerelles ne relancent pas une réponse 4xx, et le paiement serait perdu.
 */
@RestController
@RequestMapping("/api/paiements/unitechpay")
public class PaiementWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(PaiementWebhookController.class);

    /** Un webhook UnitechPay fait quelques centaines d'octets. */
    static final int TAILLE_MAX_CORPS = 16_384;

    private final LecteurNotificationsPaiement lecteur;
    private final ConfirmerPaiementUseCase confirmerPaiement;

    public PaiementWebhookController(LecteurNotificationsPaiement lecteur, ConfirmerPaiementUseCase confirmerPaiement) {
        this.lecteur = lecteur;
        this.confirmerPaiement = confirmerPaiement;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> recevoir(@RequestBody(required = false) String corps) {
        if (corps == null || corps.isBlank() || corps.length() > TAILLE_MAX_CORPS) {
            return ResponseEntity.badRequest().body(Map.of("message", "Corps de webhook absent ou trop grand"));
        }
        NotificationPaiement notification;
        try {
            notification = lecteur.lire(corps);
        } catch (SignatureWebhookInvalideException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
        try {
            ConfirmerPaiementUseCase.Resultat resultat = confirmerPaiement.traiter(notification);
            return ResponseEntity.ok(Map.of("recu", true, "resultat", resultat.name()));
        } catch (ConcurrencyFailureException e) {
            // Conflit de version après les tentatives, ou lock_timeout : passager.
            return aRelancer();
        } catch (BusinessConflictException e) {
            // Ne devrait jamais arriver (ConfirmerPaiementUseCase classe tous les refus
            // métier) : c'est un défaut. Jamais 409 ici, la passerelle ne relancerait pas.
            LOG.error("Conflit métier inattendu au traitement du webhook (référence {})", notification.reference(), e);
            return aRelancer();
        }
    }

    private static ResponseEntity<Map<String, Object>> aRelancer() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Traitement momentanément impossible, renvoyez la notification"));
    }
}
