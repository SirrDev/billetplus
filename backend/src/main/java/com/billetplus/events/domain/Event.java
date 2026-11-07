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

    /** Motif de la suspension par l'administrateur (ADR 0012, V19) ; null sinon. Événement alors ANNULE. */
    @Column(length = 500)
    private String suspenduMotif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private UUID organizerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    /** Catégorie du catalogue public (V13). Modifiable à tout moment : elle ne fait que classer. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategorieEvenement categorie;

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
        this(titre, description, debuteLe, termineLe, venue, organizerId, CategorieEvenement.AUTRE);
    }

    /** @param categorie null : AUTRE */
    public Event(String titre, String description, Instant debuteLe, Instant termineLe,
                 Venue venue, UUID organizerId, CategorieEvenement categorie) {
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
        this.categorie = categorie == null ? CategorieEvenement.AUTRE : categorie;
        this.creeLe = Instant.now();
    }

    /**
     * @throws BusinessConflictException si l'événement n'est pas en brouillon, ou si son lieu
     *         n'est pas au Sénégal (ADR 0011, point 9) (traduit en HTTP 409)
     */
    public void publier() {
        if (status != EventStatus.BROUILLON) {
            throw new BusinessConflictException("Seul un événement en brouillon peut être publié");
        }
        // ADR 0011, point 9 : numéros, heure locale et paiement des autres pays restent à faire.
        if (!venue.getVille().ouverteALaVente()) {
            throw new BusinessConflictException("La vente hors du Sénégal n'est pas encore ouverte");
        }
        this.status = EventStatus.PUBLIE;
    }

    /**
     * Refusé une seconde fois : l'annulation publie EvenementAnnule (billets annulés,
     * commandes signalées pour remboursement) ; la rejouer relancerait ces effets.
     *
     * @throws BusinessConflictException si l'événement est déjà terminé ou déjà annulé (traduit en HTTP 409)
     */
    public void annuler() {
        if (status == EventStatus.TERMINE) {
            throw new BusinessConflictException("Impossible d'annuler un événement déjà terminé");
        }
        if (status == EventStatus.ANNULE) {
            throw new BusinessConflictException("L'événement est déjà annulé");
        }
        this.status = EventStatus.ANNULE;
    }

    /**
     * Suspension par l'administrateur (ADR 0012, point 6) : annulation, avec son motif, que
     * l'organisateur lit dans son espace. Mêmes effets qu'une annulation (EvenementAnnule).
     *
     * @throws IllegalArgumentException  (400) motif absent ou trop long
     * @throws BusinessConflictException (409) événement déjà annulé ou terminé
     */
    public void suspendre(String motif) {
        String m = motif == null ? null : motif.trim();
        if (m == null || m.isEmpty() || m.length() > 500) {
            throw new IllegalArgumentException("Le motif de la suspension est obligatoire (500 caractères au plus)");
        }
        annuler();
        this.suspenduMotif = m;
    }

    /** Vrai si l'événement est encore à venir ou en cours de préparation : brouillon ou publié. */
    public boolean estActif() {
        return status == EventStatus.BROUILLON || status == EventStatus.PUBLIE;
    }

    public String getSuspenduMotif() {
        return suspenduMotif;
    }

    /**
     * Modifie le contenu de l'événement. Règles :
     * - brouillon : titre, description et dates modifiables ;
     * - publié : titre et description seulement. Des billets ont pu être vendus pour
     *   ces dates, et aucun acheteur n'est prévenu d'un changement (pas de compte,
     *   pas de notification au MVP) : un report passe par l'annulation ;
     * - annulé ou terminé : plus rien n'est modifiable.
     *
     * La catégorie, elle, reste modifiable même publié : elle classe l'événement sans rien
     * changer pour un acheteur. Null : inchangée.
     *
     * @throws IllegalArgumentException  si le titre est vide ou la fin précède le début (HTTP 400)
     * @throws BusinessConflictException si le statut interdit la modification demandée (HTTP 409)
     */
    public void modifier(String titre, String description, Instant debuteLe, Instant termineLe,
                         CategorieEvenement categorie) {
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
        if (categorie != null) {
            this.categorie = categorie;
        }
    }

    /**
     * Remplace le lieu (nouvelle position GPS), seulement tant que l'événement est un
     * brouillon. Une fois publié, la position est figée comme les dates : des acheteurs ont
     * payé pour ce lieu, et un repère déplacé après les ventes les tromperait (décision de
     * SirrDev du 06/11/2026). Le nom et l'adresse sont ceux du lieu d'origine
     * (voir EventService#definirPosition).
     *
     * @throws BusinessConflictException si l'événement n'est plus un brouillon (HTTP 409)
     */
    public void changerLieu(Venue nouveauLieu) {
        verifierLieuModifiable();
        if (nouveauLieu == null) {
            throw new IllegalArgumentException("Le lieu est obligatoire");
        }
        this.venue = nouveauLieu;
    }

    /**
     * À appeler avant de préparer un nouveau lieu, pour ne rien écrire si le changement sera refusé.
     *
     * @throws BusinessConflictException si l'événement n'est plus un brouillon (HTTP 409)
     */
    public void verifierLieuModifiable() {
        if (status == EventStatus.ANNULE || status == EventStatus.TERMINE) {
            throw new BusinessConflictException("Un événement annulé ou terminé ne peut plus être modifié");
        }
        if (status != EventStatus.BROUILLON) {
            throw new BusinessConflictException("La position d'un événement publié ne peut plus changer");
        }
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

    public CategorieEvenement getCategorie() {
        return categorie;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
