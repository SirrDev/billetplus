package com.billetplus.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entité JPA d'un reversement (V16). Jamais modifiée après insertion : toutes les
 * colonnes sont {@code updatable = false}. Package-private : seuls le mapper et
 * l'adaptateur de ce package la manipulent.
 */
@Entity
@Table(name = "reversements")
class ReversementJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private long montantFcfa;

    @Column(nullable = false, updatable = false, length = 100)
    private String reference;

    @Column(updatable = false, length = 255)
    private String motif;

    @Column(nullable = false, updatable = false)
    private LocalDate reverseLe;

    @Column(nullable = false, updatable = false)
    private UUID enregistrePar;

    @Column(nullable = false, updatable = false)
    private Instant enregistreLe;

    /** V19 (ADR 0012) : motif d'un virement sans billet scanné. */
    @Column(updatable = false, length = 255)
    private String derogationScans;

    /** V19 : copie du numéro vérifié vers lequel le virement a été fait. */
    @Column(updatable = false, length = 13)
    private String numeroReversement;

    protected ReversementJpaEntity() {
        // requis par JPA
    }

    ReversementJpaEntity(UUID id, UUID eventId, long montantFcfa, String reference, String motif,
                         LocalDate reverseLe, UUID enregistrePar, Instant enregistreLe, String derogationScans,
                         String numeroReversement) {
        this.derogationScans = derogationScans;
        this.numeroReversement = numeroReversement;
        this.id = id;
        this.eventId = eventId;
        this.montantFcfa = montantFcfa;
        this.reference = reference;
        this.motif = motif;
        this.reverseLe = reverseLe;
        this.enregistrePar = enregistrePar;
        this.enregistreLe = enregistreLe;
    }

    UUID getId() {
        return id;
    }

    UUID getEventId() {
        return eventId;
    }

    long getMontantFcfa() {
        return montantFcfa;
    }

    String getReference() {
        return reference;
    }

    String getMotif() {
        return motif;
    }

    LocalDate getReverseLe() {
        return reverseLe;
    }

    UUID getEnregistrePar() {
        return enregistrePar;
    }

    Instant getEnregistreLe() {
        return enregistreLe;
    }

    String getDerogationScans() {
        return derogationScans;
    }

    String getNumeroReversement() {
        return numeroReversement;
    }
}
