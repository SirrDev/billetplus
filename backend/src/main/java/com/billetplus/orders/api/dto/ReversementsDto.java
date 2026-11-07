package com.billetplus.orders.api.dto;

import com.billetplus.orders.application.usecase.ListerReversementsUseCase;
import com.billetplus.orders.domain.Reversement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Réponses de l'administration des reversements (ADR 0009). */
public final class ReversementsDto {

    private ReversementsDto() {
    }

    /** Une ligne de la liste : un événement et ce qu'il reste à reverser. */
    public record Ligne(UUID eventId, boolean evenementAnnule, RecettesDto recettes, LocalDate reversableLe,
                        long billetsScannes, String numeroReversement, String titulaireReversement) {

        public static Ligne from(ListerReversementsUseCase.LigneReversement ligne) {
            return new Ligne(ligne.eventId(), ligne.evenementAnnule(), RecettesDto.from(ligne.recettes()),
                ligne.reversableLe(), ligne.billetsScannes(), ligne.numeroReversement(), ligne.titulaireReversement());
        }
    }

    /** Un reversement enregistré. */
    public record Enregistre(UUID id, UUID eventId, long montantFcfa, String reference, String motif,
                             LocalDate reverseLe, Instant enregistreLe, String derogationScans,
                             String numeroReversement) {

        public static Enregistre from(Reversement r) {
            return new Enregistre(r.getId(), r.getEventId(), r.getMontantFcfa(), r.getReference(), r.getMotif(),
                r.getReverseLe(), r.getEnregistreLe(), r.getDerogationScans(), r.getNumeroReversement());
        }
    }
}
