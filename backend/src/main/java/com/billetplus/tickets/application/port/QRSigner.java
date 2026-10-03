package com.billetplus.tickets.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — signature et vérification des QR codes de
 * billets. L'implémentation concrète (Ed25519) vit en infrastructure/crypto,
 * jamais référencée directement par le domaine ou les use cases.
 *
 * Signature asymétrique plutôt qu'un secret HMAC partagé — voir ADR 0004 :
 * seul le backend détient la clé privée, la PWA scanner n'embarque que la
 * clé publique, ce qui permet une vérification 100% hors ligne sans exposer
 * de secret exploitable dans le bundle JavaScript.
 */
public interface QRSigner {

    /**
     * Signe le triplet (ticketId, eventId, issuedAt) et retourne le contenu
     * complet à encoder dans le QR code, au format "{json}.{signatureHex}".
     */
    String sign(UUID ticketId, UUID eventId, Instant issuedAt);

    /**
     * Vérifie un contenu de QR code scanné. Ne lève jamais d'exception sur
     * un contenu invalide ou falsifié — retourne un résultat invalide,
     * laissant l'appelant (use case) décider de la réponse à l'utilisateur.
     */
    QrVerificationResult verify(String qrContent);
}
