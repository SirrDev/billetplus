package com.billetplus.orders.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Virement fait à la main par l'administrateur vers l'organisateur d'un événement
 * (ADR 0009). Jamais modifié ni supprimé : une erreur se corrige par un reversement
 * négatif, toujours motivé. Montant signé en FCFA (un {@code Money} est toujours positif).
 */
public final class Reversement {

    /** Borne de saisie : bien au-delà de toute recette réaliste, sans risque de débordement. */
    public static final long MONTANT_MAX_FCFA = 1_000_000_000_000L;
    public static final int REFERENCE_MAX = 100;
    public static final int MOTIF_MAX = 255;

    private final UUID id;
    private final UUID eventId;
    private final long montantFcfa;
    private final String reference;
    private final String motif;
    private final LocalDate reverseLe;
    private final UUID enregistrePar;
    private final Instant enregistreLe;

    private Reversement(UUID id, UUID eventId, long montantFcfa, String reference, String motif,
                        LocalDate reverseLe, UUID enregistrePar, Instant enregistreLe) {
        this.id = id;
        this.eventId = eventId;
        this.montantFcfa = montantFcfa;
        this.reference = reference;
        this.motif = motif;
        this.reverseLe = reverseLe;
        this.enregistrePar = enregistrePar;
        this.enregistreLe = enregistreLe;
    }

    /**
     * @param montantFcfa positif pour un virement, négatif pour corriger une erreur (motif obligatoire)
     * @param reverseLe   date du virement, jamais dans le futur (heure de Dakar = UTC)
     * @throws IllegalArgumentException si une règle n'est pas respectée (HTTP 400)
     */
    public static Reversement enregistrer(UUID eventId, long montantFcfa, String reference, String motif,
                                          LocalDate reverseLe, UUID enregistrePar, Instant maintenant) {
        if (eventId == null) {
            throw new IllegalArgumentException("L'événement est obligatoire");
        }
        if (enregistrePar == null) {
            throw new IllegalArgumentException("L'administrateur est obligatoire");
        }
        if (maintenant == null) {
            throw new IllegalArgumentException("La date d'enregistrement est obligatoire");
        }
        if (montantFcfa == 0) {
            throw new IllegalArgumentException("Le montant d'un reversement ne peut pas être nul");
        }
        if (Math.abs(montantFcfa) > MONTANT_MAX_FCFA) {
            throw new IllegalArgumentException("Montant hors limites");
        }
        String referenceNettoyee = reference == null ? "" : reference.trim();
        if (referenceNettoyee.isEmpty() || referenceNettoyee.length() > REFERENCE_MAX) {
            throw new IllegalArgumentException("La référence du virement est obligatoire (100 caractères au plus)");
        }
        String motifNettoye = motif == null || motif.isBlank() ? null : motif.trim();
        if (motifNettoye != null && motifNettoye.length() > MOTIF_MAX) {
            throw new IllegalArgumentException("Le motif ne doit pas dépasser 255 caractères");
        }
        if (montantFcfa < 0 && motifNettoye == null) {
            throw new IllegalArgumentException("Une correction (montant négatif) doit être motivée");
        }
        if (reverseLe == null) {
            throw new IllegalArgumentException("La date du virement est obligatoire");
        }
        if (reverseLe.isAfter(LocalDate.ofInstant(maintenant, ZoneOffset.UTC))) {
            throw new IllegalArgumentException("La date du virement ne peut pas être dans le futur");
        }
        return new Reversement(UUID.randomUUID(), eventId, montantFcfa, referenceNettoyee, motifNettoye, reverseLe,
            enregistrePar, maintenant.truncatedTo(ChronoUnit.MICROS));
    }

    /** Reconstruction depuis la persistance : l'état est supposé déjà valide en base. */
    public static Reversement reconstituer(UUID id, UUID eventId, long montantFcfa, String reference, String motif,
                                           LocalDate reverseLe, UUID enregistrePar, Instant enregistreLe) {
        return new Reversement(id, eventId, montantFcfa, reference, motif, reverseLe, enregistrePar, enregistreLe);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getMontantFcfa() {
        return montantFcfa;
    }

    public String getReference() {
        return reference;
    }

    public String getMotif() {
        return motif;
    }

    public LocalDate getReverseLe() {
        return reverseLe;
    }

    public UUID getEnregistrePar() {
        return enregistrePar;
    }

    public Instant getEnregistreLe() {
        return enregistreLe;
    }
}
