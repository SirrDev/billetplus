package com.billetplus.events.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Les limites de taille reflètent le schéma (V3 : venues.nom est un VARCHAR(255)) :
 * sans elles, un nom trop long passerait la validation puis serait refusé par
 * PostgreSQL, et l'API répondrait 500 au lieu de 400.
 *
 * Latitude et longitude restent optionnelles (null accepté) mais, si elles sont
 * fournies, elles doivent être des coordonnées géographiques valides.
 */
public record CreateVenueRequest(
    @NotBlank
    @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères")
    String nom,

    @NotBlank
    @Size(max = 500, message = "L'adresse ne doit pas dépasser 500 caractères")
    String adresse,

    @DecimalMin(value = "-90.0", message = "La latitude doit être comprise entre -90 et 90")
    @DecimalMax(value = "90.0", message = "La latitude doit être comprise entre -90 et 90")
    Double latitude,

    @DecimalMin(value = "-180.0", message = "La longitude doit être comprise entre -180 et 180")
    @DecimalMax(value = "180.0", message = "La longitude doit être comprise entre -180 et 180")
    Double longitude,

    /** Code d'une ville de la liste fermée (ADR 0011), par exemple SN-DAKAR ; inconnu : 400. */
    @NotBlank(message = "La ville est obligatoire")
    @Size(max = 40, message = "Ville inconnue")
    String villeCode
) {}
