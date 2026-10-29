package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.domain.MotifSansBillet;
import com.billetplus.orders.domain.OrderStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entité JPA (niveau 3, voir ADR 0002) — représentation de persistance de
 * {@link com.billetplus.orders.domain.Order}, distincte du domaine.
 * Alignée colonne par colonne sur V8__create_orders.sql.
 *
 * Les lignes sont en LAZY (open-in-view: false) : SpringDataOrderRepository les
 * charge avec la commande par @EntityGraph.
 *
 * Package-private : seuls le mapper et l'adaptateur de ce package la manipulent.
 */
@Entity
@Table(name = "orders")
class OrderJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false, length = 13)
    private String telephone;

    @Column(nullable = false, updatable = false, length = 64)
    private String empreinteJeton;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private Instant creeLe;

    @Column(nullable = false, updatable = false)
    private Instant expireLe;

    private Instant payeeLe;

    @Enumerated(EnumType.STRING)
    private MotifSansBillet motifSansBillet;

    @ElementCollection
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLineEmbeddable> lignes = new ArrayList<>();

    /**
     * Verrouillage optimiste, absent du domaine. Wrapper null à la création :
     * persist au lieu de merge.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected OrderJpaEntity() {
        // requis par JPA
    }

    OrderJpaEntity(UUID id, UUID eventId, String telephone, String empreinteJeton, OrderStatus status,
                   Instant creeLe, Instant expireLe, Instant payeeLe, MotifSansBillet motifSansBillet,
                   List<OrderLineEmbeddable> lignes) {
        this.id = id;
        this.eventId = eventId;
        this.telephone = telephone;
        this.empreinteJeton = empreinteJeton;
        this.status = status;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
        this.payeeLe = payeeLe;
        this.motifSansBillet = motifSansBillet;
        this.lignes = new ArrayList<>(lignes);
    }

    UUID getId() {
        return id;
    }

    UUID getEventId() {
        return eventId;
    }

    String getTelephone() {
        return telephone;
    }

    String getEmpreinteJeton() {
        return empreinteJeton;
    }

    OrderStatus getStatus() {
        return status;
    }

    void setStatus(OrderStatus status) {
        this.status = status;
    }

    Instant getCreeLe() {
        return creeLe;
    }

    Instant getExpireLe() {
        return expireLe;
    }

    Instant getPayeeLe() {
        return payeeLe;
    }

    void setPayeeLe(Instant payeeLe) {
        this.payeeLe = payeeLe;
    }

    MotifSansBillet getMotifSansBillet() {
        return motifSansBillet;
    }

    void setMotifSansBillet(MotifSansBillet motifSansBillet) {
        this.motifSansBillet = motifSansBillet;
    }

    List<OrderLineEmbeddable> getLignes() {
        return lignes;
    }

    Long getVersion() {
        return version;
    }
}
