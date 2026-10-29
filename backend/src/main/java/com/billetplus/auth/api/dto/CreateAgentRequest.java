package com.billetplus.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Création d'un compte AGENT par un organisateur. Mêmes règles que
 * RegisterRequest (schéma V2, limite de BCrypt) ; volontairement sans rôle ni
 * créateur : le rôle est fixé par le serveur, le créateur vient du jeton.
 */
public record CreateAgentRequest(
    @NotBlank
    @Email
    @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
    String email,

    @NotBlank
    @Size(min = 8, max = 72, message = "Le mot de passe doit faire entre 8 et 72 caractères")
    String motDePasse,

    @NotBlank
    @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères")
    String nomComplet
) {}
