package com.billetplus.auth.api;

import com.billetplus.auth.api.dto.AgentDto;
import com.billetplus.auth.api.dto.CreateAgentRequest;
import com.billetplus.auth.application.AgentAccountService;
import com.billetplus.auth.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Comptes AGENT d'un organisateur (contrôle d'entrée). L'agent se connecte
 * ensuite par POST /api/auth/login, comme tout compte.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentAccountService agentAccountService;

    public AgentController(AgentAccountService agentAccountService) {
        this.agentAccountService = agentAccountService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<AgentDto> create(@Valid @RequestBody CreateAgentRequest request, Authentication auth) {
        UUID organisateurId = (UUID) auth.getPrincipal();
        User agent = agentAccountService.creerAgent(
            organisateurId, request.email(), request.motDePasse(), request.nomComplet());
        return ResponseEntity.created(URI.create("/api/agents/" + agent.getId())).body(AgentDto.from(agent));
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<List<AgentDto>> mine(Authentication auth) {
        UUID organisateurId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(agentAccountService.listerMesAgents(organisateurId).stream().map(AgentDto::from).toList());
    }
}
