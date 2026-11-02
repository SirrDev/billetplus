package com.billetplus.events.api.dto;

import com.billetplus.events.domain.Event;

import java.time.Instant;
import java.util.UUID;

public record EventDto(
    UUID id,
    String titre,
    String description,
    Instant debuteLe,
    Instant termineLe,
    UUID venueId,
    String venueNom,
    UUID organizerId,
    String status,
    Instant creeLe,
    String categorie
) {
    public static EventDto from(Event event) {
        return new EventDto(
            event.getId(), event.getTitre(), event.getDescription(),
            event.getDebuteLe(), event.getTermineLe(),
            event.getVenue().getId(), event.getVenue().getNom(),
            event.getOrganizerId(), event.getStatus().name(), event.getCreeLe(),
            event.getCategorie().name()
        );
    }
}
