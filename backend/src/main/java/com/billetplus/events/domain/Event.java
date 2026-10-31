package com.billetplus.events.domain;

import com.billetplus.shared.exception.BusinessConflictException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
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

    /**
     * Verrouillage optimiste (V11) : publier, annuler et modifier lisent puis réécrivent
     * toute la ligne. Sans lui, une modification concurrente d'une annulation pourrait
     * remettre l'ancien statut après l'annulation des billets. Conflit → 409
     * (OptimisticLockingFailureException, GlobalExceptionHandler). Wrapper null à la
     * création : persist au lieu de merge.
     */
    @Version
    @Column(nullable = false)
    private Long version;

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

    /**
     * @throws BusinessConflictException si l'événement n'est pas en brouillon (traduit en HTTP 409)
     */
    public void publier() {
        if (status != EventStatus.BROUILLON) {
            throw new BusinessConflictException("Seul un événement en brouillon peut être publié");
        }
        this.status = EventStatus.PUBLIE;
    }

    /**
     * @throws BusinessConflictException si l'événement est déjà terminé (traduit en HTTP 409)
     */
    public void annuler() {
        if (status == EventStatus.TERMINE) {
            throw new BusinessConflictException("Impossible d'annuler un événement déjà terminé");
        }
        this.status = EventStatus.ANNULE;
    }

    /**
     * Modifie le contenu de l'événement. Règles :
     * - brouillon : titre, description et dates modifiables ;
     * - publié : titre et description seulement. Des billets ont pu être vendus pour
     *   ces dates, et aucun acheteur n'est prévenu d'un changement (pas de compte,
     *   pas de notification au MVP) : un report passe par l'annulation ;
     * - annulé ou terminé : plus rien n'est modifiable.
     *
     * @throws IllegalArgumentException  si le titre est vide ou la fin précède le début (HTTP 400)
     * @throws BusinessConflictException si le statut interdit la modification demandée (HTTP 409)
     */
    public void modifier(String titre, String description, Instant debuteLe, Instant termineLe) {
        if (status == EventStatus.ANNULE || status == EventStatus.TERMINE) {
            throw new BusinessConflictException("Un événement annulé ou terminé ne peut plus être modifié");
        }
        if (titre == null || titre.isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire");
        }
        if (debuteLe == null) {
            throw new IllegalArgumentException("La date de début est obligatoire");
        }
        if (termineLe != null && termineLe.isBefore(debuteLe)) {
            throw new IllegalArgumentException("La date de fin ne peut pas précéder la date de début");
        }
        boolean datesChangees = !debuteLe.equals(this.debuteLe) || !Objects.equals(termineLe, this.termineLe);
        if (status == EventStatus.PUBLIE && datesChangees) {
            throw new BusinessConflictException(
                "Les dates d'un événement publié ne peuvent plus changer : annulez-le puis créez-en un nouveau");
        }
        this.titre = titre;
        this.description = description;
        this.debuteLe = debuteLe;
        this.termineLe = termineLe;
    }

    public boolean estPublie() {
        return status == EventStatus.PUBLIE;
    }

    /**
     * Règle de visibilité : un brouillon n'est visible que par son organisateur.
     * Un événement publié, annulé ou terminé est public (les acheteurs doivent
     * pouvoir constater une annulation).
     *
     * @param viewerId identifiant du visiteur, ou null s'il n'est pas connecté
     */
    public boolean estVisiblePar(UUID viewerId) {
        // organizerId n'est jamais null (garanti par le constructeur et la colonne NOT NULL) ;
        // organizerId.equals(null) vaut false, ce qui couvre aussi le visiteur anonyme.
        return status != EventStatus.BROUILLON || organizerId.equals(viewerId);
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
