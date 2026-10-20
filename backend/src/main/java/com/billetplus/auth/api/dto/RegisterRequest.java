package com.billetplus.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de l'inscription PUBLIQUE (POST /api/auth/register, accessible sans jeton).
 *
 * Volontairement SANS champ « role » : le client ne choisit jamais son rôle.
 * Un compte créé par cette route est toujours un ORGANISATEUR (voir
 * AuthService.registerOrganisateur). Si un client envoie quand même un champ
 * « role », Jackson l'ignore (Spring Boot désactive FAIL_ON_UNKNOWN_PROPERTIES) :
 * la valeur n'est jamais lue, donc elle ne peut produire aucun effet.
 *
 * Les limites de taille reflètent le schéma (V2 : users.email et users.nom_complet
 * sont des VARCHAR(255)) : sans elles, une valeur trop longue passerait la validation
 * puis serait refusée par PostgreSQL, et l'API répondrait 500 au lieu de 400.
 * Le maximum de 72 caractères sur le mot de passe correspond à la limite de BCrypt,
 * qui ignore tout ce qui dépasse 72 octets.
 */
public record RegisterRequest(
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
