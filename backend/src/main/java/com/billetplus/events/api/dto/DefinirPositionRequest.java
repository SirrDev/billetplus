package com.billetplus.events.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Corps de PUT /api/events/{id}/position : mêmes bornes que CreateVenueRequest, mais obligatoires. */
public record DefinirPositionRequest(
    @NotNull
    @DecimalMin(value = "-90.0", message = "La latitude doit être comprise entre -90 et 90")
    @DecimalMax(value = "90.0", message = "La latitude doit être comprise entre -90 et 90")
    Double latitude,

    @NotNull
    @DecimalMin(value = "-180.0", message = "La longitude doit être comprise entre -180 et 180")
    @DecimalMax(value = "180.0", message = "La longitude doit être comprise entre -180 et 180")
    Double longitude
) {}
