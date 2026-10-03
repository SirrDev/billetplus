package com.billetplus.auth.api.dto;

import com.billetplus.auth.domain.User;

import java.util.UUID;

public record MeResponse(UUID id, String email, String nomComplet, String role) {
    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getNomComplet(), user.getRole().name());
    }
}
