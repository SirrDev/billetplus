package com.billetplus.tickets.application.port;

import java.time.Instant;
import java.util.UUID;

public record QrVerificationResult(boolean valide, UUID ticketId, UUID eventId, Instant issuedAt) {

    public static QrVerificationResult invalide() {
        return new QrVerificationResult(false, null, null, null);
    }

    public static QrVerificationResult valide(UUID ticketId, UUID eventId, Instant issuedAt) {
        return new QrVerificationResult(true, ticketId, eventId, issuedAt);
    }
}
