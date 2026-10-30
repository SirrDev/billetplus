package com.billetplus.orders.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — le paiement ouvert pour une commande
 * chez la passerelle (ADR 0007). Une commande en a au plus un.
 *
 * @param reference     référence de la passerelle, unique : clé de rapprochement du webhook
 * @param transactionId identifiant de transaction de la passerelle (informatif, jamais utilisé pour décider)
 * @param url           lien de paiement à ouvrir par l'acheteur
 */
public record Paiement(MoyenPaiement moyen, String reference, long transactionId, String url, Instant ouvertLe) {

    public Paiement {
        if (moyen == null) {
            throw new IllegalArgumentException("Le moyen de paiement est obligatoire");
        }
        if (reference == null || reference.isBlank() || reference.length() > 100) {
            throw new IllegalArgumentException("Référence de paiement absente ou trop longue");
        }
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("Le lien de paiement doit être une URL https");
        }
        if (ouvertLe == null) {
            throw new IllegalArgumentException("La date d'ouverture du paiement est obligatoire");
        }
        // Précision de TIMESTAMPTZ, comme les autres dates de la commande.
        ouvertLe = ouvertLe.truncatedTo(ChronoUnit.MICROS);
    }
}
