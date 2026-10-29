package com.billetplus.tickets.infrastructure.persistence;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Entité JPA (niveau 3, voir ADR 0002) — représentation de persistance de
 * {@link com.billetplus.tickets.domain.TicketCategory}, distincte du domaine.
 * Alignée colonne par colonne sur V4__create_ticket_categories_and_tickets.sql.
 *
 * Package-private : seuls le mapper et l'adaptateur de ce package la manipulent.
 */
@Entity
@Table(name = "ticket_categories")
class TicketCategoryJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private String nom;

    @Column(nullable = false, updatable = false)
    private Long prixFcfa;

    @Column(nullable = false, updatable = false)
    private Integer quotaTotal;

    @Column(nullable = false)
    private Integer quotaVendu;

    /**
     * Verrouillage optimiste, absent du domaine. Type wrapper volontaire :
     * null à la création, Spring Data considère l'entité comme nouvelle et
     * fait un persist (INSERT) au lieu d'un merge, malgré l'@Id assigné en mémoire.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected TicketCategoryJpaEntity() {
        // requis par JPA
    }

    TicketCategoryJpaEntity(UUID id, UUID eventId, String nom, Long prixFcfa,
                            Integer quotaTotal, Integer quotaVendu) {
        this.id = id;
        this.eventId = eventId;
        this.nom = nom;
        this.prixFcfa = prixFcfa;
        this.quotaTotal = quotaTotal;
        this.quotaVendu = quotaVendu;
    }

    UUID getId() {
        return id;
    }

    UUID getEventId() {
        return eventId;
    }

    String getNom() {
        return nom;
    }

    Long getPrixFcfa() {
        return prixFcfa;
    }

    Integer getQuotaTotal() {
        return quotaTotal;
    }

    Integer getQuotaVendu() {
        return quotaVendu;
    }

    void setQuotaVendu(Integer quotaVendu) {
        this.quotaVendu = quotaVendu;
    }

    Long getVersion() {
        return version;
    }
}
