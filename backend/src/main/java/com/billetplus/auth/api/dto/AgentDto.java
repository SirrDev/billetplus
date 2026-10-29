package com.billetplus.auth.api.dto;

import com.billetplus.auth.domain.User;

import java.time.Instant;
import java.util.UUID;

/** Compte agent vu par son organisateur : jamais le hash du mot de passe. */
public record AgentDto(UUID id, String email, String nomComplet, Instant creeLe) {

    public static AgentDto from(User agent) {
        return new AgentDto(agent.getId(), agent.getEmail(), agent.getNomComplet(), agent.getCreeLe());
    }
}
