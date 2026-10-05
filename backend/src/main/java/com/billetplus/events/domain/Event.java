package com.billetplus.events.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Module events — niveau 2 (JPA-as-domain), voir ADR 0002.
 *
 * organizerId référence un User du module auth PAR IDENTIFIANT UNIQUEMENT
 * (UUID), jamais par relation JPA (@ManyToOne) — règle d'isolation inter-module
 * vérifiée par ArchitectureTest.modules_sont_independants (voir ADR 0001).
 * À l'inverse, la relation vers Venue est une vraie @ManyToOne JPA : Venue
 * vit dans ce même module events, aucune frontière à protéger ici.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant debuteLe;

    private Instant termineLe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private UUID organizerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(nullable = false, updatable = false)
    private Instant creeLe;

    protected Event() {
        // requis par JPA
    }

    public Event(String titre, String description, Instant debuteLe, Instant termineLe,
                 Venue venue, UUID organizerId) {
        if (titre == null || titre.isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire");
        }
        if (debuteLe == null) {
            throw new IllegalArgumentException("La date de début est obligatoire");
        }
        if (termineLe != null && termineLe.isBefore(debuteLe)) {
            throw new IllegalArgumentException("La date de fin ne peut pas précéder la date de début");
        }
        if (venue == null) {
            throw new IllegalArgumentException("Le lieu est obligatoire");
        }
        if (organizerId == null) {
            throw new IllegalArgumentException("L'organisateur est obligatoire");
        }
        this.id = UUID.randomUUID();
        this.titre = titre;
        this.description = description;
        this.debuteLe = debuteLe;
        this.termineLe = termineLe;
        this.venue = venue;
        this.organizerId = organizerId;
        this.status = EventStatus.BROUILLON;
        this.creeLe = Instant.now();
    }

    public void publier() {
        if (status != EventStatus.BROUILLON) {
            throw new IllegalStateException("Seul un événement en brouillon peut être publié");
        }
        this.status = EventStatus.PUBLIE;
    }

    public void annuler() {
        if (status == EventStatus.TERMINE) {
            throw new IllegalStateException("Impossible d'annuler un événement déjà terminé");
        }
        this.status = EventStatus.ANNULE;
    }

    public boolean estPublie() {
        return status == EventStatus.PUBLIE;
    }

    public UUID getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public String getDescription() {
        return description;
    }

    public Instant getDebuteLe() {
        return debuteLe;
    }

    public Instant getTermineLe() {
        return termineLe;
    }

    public Venue getVenue() {
        return venue;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
