package com.billetplus.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/**
 * Module auth — niveau 2 (JPA-as-domain). Une pièce déposée par un organisateur (ADR 0012),
 * stockée CHIFFRÉE : {@code contenu} est déjà chiffré (nonce + texte chiffré) quand
 * l'entité est construite ; elle ne voit jamais le fichier en clair.
 */
@Entity
@Table(name = "pieces_organisateurs")
public class PieceOrganisateur {

    public static final int TAILLE_MAX = 5 * 1024 * 1024;
    public static final Set<String> TYPES_ACCEPTES = Set.of("application/pdf", "image/jpeg", "image/png");

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TypePiece type;

    @Column(nullable = false, updatable = false, length = 30)
    private String typeMime;

    @Column(nullable = false, updatable = false)
    private int taille;

    @Column(nullable = false, updatable = false)
    private byte[] contenu;

    @Column(nullable = false, updatable = false)
    private Instant deposeLe;

    protected PieceOrganisateur() {
        // requis par JPA
    }

    /**
     * @param taille        taille du fichier en clair, vérifiée par l'appelant (≤ {@link #TAILLE_MAX})
     * @param contenuChiffre fichier chiffré par l'application
     */
    public PieceOrganisateur(UUID organisateurId, TypePiece type, String typeMime, int taille, byte[] contenuChiffre,
                             Instant maintenant) {
        if (organisateurId == null || type == null || contenuChiffre == null || maintenant == null) {
            throw new IllegalArgumentException("Pièce incomplète");
        }
        if (!TYPES_ACCEPTES.contains(typeMime)) {
            throw new IllegalArgumentException("Format refusé : PDF, JPEG ou PNG seulement");
        }
        if (taille < 1 || taille > TAILLE_MAX) {
            throw new IllegalArgumentException("Le fichier doit faire 5 Mo au plus");
        }
        this.id = UUID.randomUUID();
        this.organisateurId = organisateurId;
        this.type = type;
        this.typeMime = typeMime;
        this.taille = taille;
        this.contenu = contenuChiffre;
        this.deposeLe = maintenant.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganisateurId() {
        return organisateurId;
    }

    public TypePiece getType() {
        return type;
    }

    public String getTypeMime() {
        return typeMime;
    }

    public int getTaille() {
        return taille;
    }

    /** Contenu chiffré, tel qu'en base. */
    public byte[] getContenu() {
        return contenu;
    }

    public Instant getDeposeLe() {
        return deposeLe;
    }
}
