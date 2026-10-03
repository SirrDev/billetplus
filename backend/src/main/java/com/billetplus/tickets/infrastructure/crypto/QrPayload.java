package com.billetplus.tickets.infrastructure.crypto;

/**
 * Représentation JSON compacte du contenu signé — noms de champs volontairement
 * courts pour limiter la densité du QR code. Doit rester synchronisé avec
 * web/src/shared/crypto/verifyTicketQR.ts côté PWA scanner (même contrat).
 */
record QrPayload(String tid, String eid, long iat) {}
