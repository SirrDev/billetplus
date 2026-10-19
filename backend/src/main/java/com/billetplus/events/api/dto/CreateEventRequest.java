package com.billetplus.events.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Volontairement sans organizerId : l'organisateur est toujours l'utilisateur
 * authentifié (identité tirée du jeton par le controller), jamais une valeur
 * fournie par le client.
 *
 * titre est limité à 255 caractères pour refléter events.titre VARCHAR(255) (V3) :
 * sans cette limite, un titre trop long ferait échouer l'insertion en base (500)
 * au lieu d'un 400 clair.
 */
public record CreateEventRequest(
    @NotBlank
    @Size(max = 255, message = "Le titre ne doit pas dépasser 255 caractères")
    String titre,

    @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères")
    String description,

    @NotNull Instant debuteLe,

    Instant termineLe,

    @NotNull UUID venueId
) {}
