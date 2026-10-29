package com.billetplus.tickets.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Volontairement sans eventId ni organisateur : l'événement vient de l'URL et
 * l'organisateur du jeton, jamais du corps de la requête.
 *
 * Bornes alignées sur le schéma V4 (piège 15 : une valeur hors limites ferait
 * échouer l'insertion, donc un 500 au lieu d'un 400) : nom VARCHAR(255),
 * prix_fcfa BIGINT ≥ 0, quota_total INTEGER > 0. Les plafonds hauts écartent
 * les valeurs absurdes (un milliard de FCFA, un million de places).
 */
public record CreateTicketCategoryRequest(
    @NotBlank
    @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères")
    String nom,

    @NotNull
    @Min(value = 0, message = "Le prix ne peut pas être négatif")
    @Max(value = 1_000_000_000L, message = "Le prix ne doit pas dépasser 1 000 000 000 FCFA")
    Long prixFcfa,

    @NotNull
    @Min(value = 1, message = "Le quota doit être d'au moins 1 place")
    @Max(value = 1_000_000, message = "Le quota ne doit pas dépasser 1 000 000 places")
    Integer quotaTotal
) {}
