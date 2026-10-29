package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.tickets.domain.TicketStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité JPA (niveau 3, voir ADR 0002) — représentation de persistance de
 * {@link com.billetplus.tickets.domain.Ticket}, distincte du domaine.
 * Alignée colonne par colonne sur V4__create_ticket_categories_and_tickets.sql.
 *
 * categoryId reste un UUID simple plutôt qu'un @ManyToOne : le domaine ne
 * manipule que l'identifiant, et la contrainte FK en base suffit à garantir
 * que la catégorie existe.
 *
 * Package-private : seuls le mapper et l'adaptateur de ce package la manipulent.
 */
@Entity
@Table(name = "tickets")
class TicketJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private UUID categoryId;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(nullable = false, updatable = false)
    private Instant emisLe;

    private Instant scanneLe;

    /**
     * Verrouillage optimiste (deux scans simultanés du même billet), absent du
     * domaine. Wrapper null à la création : persist au lieu de merge.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected TicketJpaEntity() {
        // requis par JPA
    }

    TicketJpaEntity(UUID id, UUID eventId, UUID categoryId, UUID orderId,
                    TicketStatus status, Instant emisLe, Instant scanneLe) {
        this.id = id;
        this.eventId = eventId;
        this.categoryId = categoryId;
        this.orderId = orderId;
        this.status = status;
        this.emisLe = emisLe;
        this.scanneLe = scanneLe;
    }

    UUID getId() {
        return id;
    }

    UUID getEventId() {
        return eventId;
    }

    UUID getCategoryId() {
        return categoryId;
    }

    UUID getOrderId() {
        return orderId;
    }

    TicketStatus getStatus() {
        return status;
    }

    void setStatus(TicketStatus status) {
        this.status = status;
    }

    Instant getEmisLe() {
        return emisLe;
    }

    Instant getScanneLe() {
        return scanneLe;
    }

    void setScanneLe(Instant scanneLe) {
        this.scanneLe = scanneLe;
    }

    Long getVersion() {
        return version;
    }
}
