package com.billetplus.tickets.domain;

import com.billetplus.tickets.domain.exception.BilletNonValideException;
import com.billetplus.tickets.domain.exception.TicketAlreadyScannedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Domaine pur (niveau 3, voir ADR 0002). Représente un billet individuel,
 * indépendamment de sa représentation QR (gérée par infrastructure/crypto,
 * voir feature/backend-tickets-ed25519-signer).
 */
public class Ticket {

    private final UUID id;
    private final UUID eventId;
    private final UUID categoryId;
    private final UUID orderId;
    private TicketStatus status;
    private final Instant emisLe;
    private Instant scanneLe;

    public Ticket(UUID eventId, UUID categoryId, UUID orderId) {
        if (eventId == null || categoryId == null || orderId == null) {
            throw new IllegalArgumentException("eventId, categoryId et orderId sont obligatoires");
        }
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.categoryId = categoryId;
        this.orderId = orderId;
        this.status = TicketStatus.EMIS;
        this.emisLe = maintenant();
    }

    public static Ticket reconstituer(UUID id, UUID eventId, UUID categoryId, UUID orderId,
                                       TicketStatus status, Instant emisLe, Instant scanneLe) {
        Ticket ticket = new Ticket(id, eventId, categoryId, orderId, emisLe);
        ticket.status = status;
        ticket.scanneLe = scanneLe;
        return ticket;
    }

    private Ticket(UUID id, UUID eventId, UUID categoryId, UUID orderId, Instant emisLe) {
        this.id = id;
        this.eventId = eventId;
        this.categoryId = categoryId;
        this.orderId = orderId;
        this.emisLe = emisLe;
    }

    /**
     * Marque le billet comme scanné. C'est l'ultime rempart contre le double
     * scan côté serveur — complète, sans la remplacer, la vérification de
     * signature côté scanner (voir ADR 0004) qui ne peut garantir l'unicité
     * en mode hors ligne.
     *
     * Les deux refus sont des conflits métier (409), pas des pannes : présenter
     * un billet déjà utilisé, annulé ou remboursé est une situation normale à
     * l'entrée. Aucun état n'est modifié en cas de refus.
     *
     * @throws TicketAlreadyScannedException si le billet a déjà été scanné
     * @throws BilletNonValideException      si le billet est annulé ou remboursé
     */
    public void marquerCommeScanne() {
        if (status == TicketStatus.SCANNE) {
            throw new TicketAlreadyScannedException(id, scanneLe);
        }
        if (status != TicketStatus.EMIS) {
            throw new BilletNonValideException(id, status);
        }
        this.status = TicketStatus.SCANNE;
        this.scanneLe = maintenant();
    }

    /**
     * Horodatage à la microseconde, la précision de TIMESTAMPTZ : un billet
     * relu en base est ainsi strictement égal au billet émis en mémoire.
     *
     * Attention : le QR code ne signe emisLe qu'à la MILLISECONDE
     * (Ed25519QRSigner, champ iat). Toute comparaison entre la date d'un QR
     * vérifié et emisLe doit donc se faire après truncatedTo(ChronoUnit.MILLIS).
     */
    private static Instant maintenant() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void annuler() {
        if (status == TicketStatus.SCANNE) {
            throw new IllegalStateException("Impossible d'annuler un billet déjà scanné");
        }
        this.status = TicketStatus.ANNULE;
    }

    public void marquerRembourse() {
        this.status = TicketStatus.REMBOURSE;
    }

    public boolean estValide() {
        return status == TicketStatus.EMIS;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getEmisLe() {
        return emisLe;
    }

    public Instant getScanneLe() {
        return scanneLe;
    }
}
