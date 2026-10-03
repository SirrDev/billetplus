package com.billetplus.auth.api.dto;

import com.billetplus.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, message = "Le mot de passe doit faire au moins 8 caractères") String motDePasse,
    @NotBlank String nomComplet,
    @NotBlank String role // ORGANISATEUR, AGENT ou ADMIN — validé dans le controller
) {
    public Role toRole() {
        return Role.valueOf(role.toUpperCase());
    }
}
