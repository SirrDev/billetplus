package com.billetplus.tickets.domain;

import com.billetplus.tickets.domain.exception.BilletDejaTransfereException;
import com.billetplus.tickets.domain.exception.BilletNonValideException;
import com.billetplus.tickets.domain.exception.TicketAlreadyScannedException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    /**
     * Date signée dans le QR en vigueur (champ iat). Égale à emisLe, puis changée par
     * un transfert : le QR précédent ne correspond plus et le contrôle en ligne le
     * refuse (ADR 0008).
     */
    private Instant qrSigneLe;
    /** Mobile du destinataire (+2217XXXXXXXX), null tant que le billet n'est pas transféré. */
    private String transfereA;
    /** Empreinte SHA-256 du jeton du destinataire (JetonTransfert), jamais le jeton. */
    private String empreinteJetonTransfert;
    private Instant transfereLe;

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
        this.qrSigneLe = emisLe;
    }

    /** Billet relu en base, jamais transféré. */
    public static Ticket reconstituer(UUID id, UUID eventId, UUID categoryId, UUID orderId,
                                       TicketStatus status, Instant emisLe, Instant scanneLe) {
        return reconstituer(id, eventId, categoryId, orderId, status, emisLe, scanneLe, emisLe, null, null, null);
    }

    /** Billet relu en base, avec son QR en vigueur et son éventuel transfert. */
    public static Ticket reconstituer(UUID id, UUID eventId, UUID categoryId, UUID orderId,
                                       TicketStatus status, Instant emisLe, Instant scanneLe,
                                       Instant qrSigneLe, String transfereA, String empreinteJetonTransfert,
                                       Instant transfereLe) {
        Ticket ticket = new Ticket(id, eventId, categoryId, orderId, emisLe);
        ticket.status = status;
        ticket.scanneLe = scanneLe;
        ticket.qrSigneLe = qrSigneLe == null ? emisLe : qrSigneLe;
        ticket.transfereA = transfereA;
        ticket.empreinteJetonTransfert = empreinteJetonTransfert;
        ticket.transfereLe = transfereLe;
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

    /**
     * Transfère le billet à un autre mobile (ADR 0008). Définitif : un billet transféré
     * ne l'est pas une seconde fois, sinon un vendeur pourrait reprendre un billet déjà
     * cédé. Le QR est re-signé avec une nouvelle date : l'ancien QR, resté chez
     * l'expéditeur, est refusé par le contrôle en ligne.
     *
     * @param telephoneE164   mobile du destinataire, déjà normalisé (+2217XXXXXXXX)
     * @param empreinteJeton  empreinte SHA-256 du jeton remis au destinataire
     * @throws BilletNonValideException  si le billet n'est plus valable (scanné, annulé, remboursé)
     * @throws BilletDejaTransfereException si le billet a déjà été transféré
     */
    public void transfererA(String telephoneE164, String empreinteJeton) {
        if (telephoneE164 == null || !telephoneE164.matches("\\+2217\\d{8}")) {
            throw new IllegalArgumentException("Numéro de mobile sénégalais invalide");
        }
        if (empreinteJeton == null || !empreinteJeton.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Empreinte de jeton invalide");
        }
        if (estTransfere()) {
            throw new BilletDejaTransfereException(id);
        }
        if (status != TicketStatus.EMIS) {
            throw new BilletNonValideException(id, status);
        }
        Instant maintenant = maintenant();
        // Le QR ne signe qu'à la milliseconde : garantir une milliseconde différente,
        // sinon l'ancien QR resterait valable.
        if (maintenant.truncatedTo(ChronoUnit.MILLIS).equals(qrSigneLe.truncatedTo(ChronoUnit.MILLIS))) {
            maintenant = qrSigneLe.truncatedTo(ChronoUnit.MILLIS).plusMillis(1);
        }
        this.qrSigneLe = maintenant;
        this.transfereA = telephoneE164;
        this.empreinteJetonTransfert = empreinteJeton;
        this.transfereLe = maintenant;
    }

    public boolean estTransfere() {
        return transfereA != null;
    }

    /**
     * Vrai si le billet est transféré à ce mobile et que le jeton correspond. Faux pour
     * un billet non transféré.
     *
     * Les deux facteurs sont TOUJOURS évalués, en temps constant, puis combinés sans
     * court-circuit (&) : la durée de la réponse ne doit pas dire si le numéro était le
     * bon (relecture de sécurité de l'ADR 0008).
     */
    public boolean appartientAuDestinataire(String telephoneE164, String jetonPresente) {
        boolean jetonCorrect = JetonTransfert.correspond(jetonPresente, empreinteJetonTransfert);
        boolean numeroCorrect = transfereA != null && telephoneE164 != null && MessageDigest.isEqual(
            transfereA.getBytes(StandardCharsets.UTF_8), telephoneE164.getBytes(StandardCharsets.UTF_8));
        return estTransfere() & numeroCorrect & jetonCorrect;
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

    public Instant getQrSigneLe() {
        return qrSigneLe;
    }

    public String getTransfereA() {
        return transfereA;
    }

    public String getEmpreinteJetonTransfert() {
        return empreinteJetonTransfert;
    }

    public Instant getTransfereLe() {
        return transfereLe;
    }
}
