package com.billetplus.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Modification d'un compte AGENT par son organisateur. Limites alignées sur le schéma
 * (V2 : users.email et users.nom_complet VARCHAR(255)). Ni rôle ni créateur : ils ne
 * changent jamais.
 */
public record UpdateAgentRequest(
    @NotBlank
    @Email
    @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
    String email,

    @NotBlank
    @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères")
    String nomComplet
) {}
