package com.billetplus.tickets.infrastructure.persistence;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité JPA du registre d'idempotence des émissions, alignée sur
 * V5__create_ticket_issuances.sql. Aucune contrepartie dans le domaine : ce
 * registre ne porte pas de règle métier, seulement une contrainte d'unicité.
 *
 * Ligne en insertion seule : elle n'est jamais modifiée après création.
 * L'adaptateur insère par requête native (ON CONFLICT DO NOTHING) ; l'entité
 * reste déclarée pour que ddl-auto: validate contrôle l'alignement avec V5,
 * et pour de futures lectures. Package-private.
 */
@Entity
@Table(name = "ticket_issuances")
@IdClass(TicketIssuanceJpaEntity.Cle.class)
class TicketIssuanceJpaEntity {

    @Id
    private UUID orderId;

    @Id
    private UUID categoryId;

    @Column(nullable = false, updatable = false)
    private Integer quantite;

    @Column(nullable = false, updatable = false)
    private Instant emiseLe;

    protected TicketIssuanceJpaEntity() {
        // requis par JPA
    }

    TicketIssuanceJpaEntity(UUID orderId, UUID categoryId, Integer quantite, Instant emiseLe) {
        this.orderId = orderId;
        this.categoryId = categoryId;
        this.quantite = quantite;
        this.emiseLe = emiseLe;
    }

    /** Clé composite (order_id, category_id), exigée par @IdClass. */
    static class Cle implements Serializable {

        private UUID orderId;
        private UUID categoryId;

        protected Cle() {
            // requis par JPA
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Cle autre)) return false;
            return Objects.equals(orderId, autre.orderId) && Objects.equals(categoryId, autre.categoryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId, categoryId);
        }
    }
}
