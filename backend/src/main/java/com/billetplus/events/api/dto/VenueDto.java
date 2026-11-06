package com.billetplus.events.api.dto;

import com.billetplus.events.domain.Venue;

import java.util.UUID;

public record VenueDto(
    UUID id,
    String nom,
    String adresse,
    Double latitude,
    Double longitude,
    String seatingPlan,
    /** Ville du lieu (ADR 0011) : code de la liste, nom affiché, pays (code ISO). */
    String villeCode,
    String villeNom,
    String pays,
    String paysNom
) {
    public static VenueDto from(Venue venue) {
        return new VenueDto(
            venue.getId(), venue.getNom(), venue.getAdresse(),
            venue.getLatitude(), venue.getLongitude(), venue.getSeatingPlan(),
            venue.getVille().getCode(), venue.getVille().getNom(), venue.getVille().getPays().name(), venue.getVille().getPays().getNom()
        );
    }
}
