package com.billetplus.events.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Corps de PUT /api/events/{id} : remplace le contenu modifiable de l'événement.
 * Ni organisateur, ni statut, ni lieu : l'organisateur vient du jeton, le statut
 * change par publier / annuler, et le lieu n'est pas modifiable au MVP.
 *
 * Pour un événement publié, le client renvoie les dates reçues, inchangées : toute
 * différence est refusée en 409 (voir Event#modifier). Limites alignées sur
 * CreateEventRequest (events.titre VARCHAR(255)).
 */
public record UpdateEventRequest(
    @NotBlank
    @Size(max = 255, message = "Le titre ne doit pas dépasser 255 caractères")
    String titre,

    @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères")
    String description,

    @NotNull Instant debuteLe,

    Instant termineLe
) {}
