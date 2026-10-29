package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.domain.Ticket;

/**
 * Résultat d'une émission : le billet persisté et le contenu signé de son
 * QR code ("{json}.{signatureHex}", voir QRSigner et ADR 0004).
 */
public record IssuedTicket(Ticket ticket, String qrContent) {
}
