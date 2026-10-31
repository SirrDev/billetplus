package com.billetplus.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de POST /api/auth/mot-de-passe. Le compte concerné est celui du jeton,
 * jamais un identifiant fourni par le client.
 *
 * Le mot de passe actuel est seulement borné (255 caractères, taille de l'entrée) :
 * c'est une vérification, pas une règle de format. Le nouveau suit la règle de
 * l'inscription : 8 à 72 caractères (limite de BCrypt).
 */
public record ChangerMotDePasseRequest(
    @NotBlank
    @Size(max = 255, message = "Le mot de passe actuel ne doit pas dépasser 255 caractères")
    String motDePasseActuel,

    @NotBlank
    @Size(min = 8, max = 72, message = "Le mot de passe doit faire entre 8 et 72 caractères")
    String nouveauMotDePasse
) {}
