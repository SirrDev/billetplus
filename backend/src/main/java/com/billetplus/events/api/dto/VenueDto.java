package com.billetplus.events.api.dto;

import com.billetplus.events.domain.Venue;

import java.util.UUID;

public record VenueDto(
    UUID id,
    String nom,
    String adresse,
    Double latitude,
    Double longitude,
    String seatingPlan
) {
    public static VenueDto from(Venue venue) {
        return new VenueDto(
            venue.getId(), venue.getNom(), venue.getAdresse(),
            venue.getLatitude(), venue.getLongitude(), venue.getSeatingPlan()
        );
    }
}
