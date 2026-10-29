package com.billetplus.events.api.dto;

import com.billetplus.events.domain.Event;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement tel que le voit le public dans la liste. Volontairement sans
 * organizerId ni creeLe : la liste publique n'expose que ce qu'un acheteur
 * doit savoir pour choisir (le détail complet reste GET /api/events/{id}).
 */
public record EvenementPublicDto(
    UUID id,
    String titre,
    String description,
    Instant debuteLe,
    Instant termineLe,
    UUID venueId,
    String venueNom,
    String venueAdresse
) {
    public static EvenementPublicDto from(Event event) {
        return new EvenementPublicDto(
            event.getId(), event.getTitre(), event.getDescription(),
            event.getDebuteLe(), event.getTermineLe(),
            event.getVenue().getId(), event.getVenue().getNom(), event.getVenue().getAdresse()
        );
    }
}
