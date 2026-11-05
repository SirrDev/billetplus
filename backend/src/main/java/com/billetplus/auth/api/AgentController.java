package com.billetplus.auth.api;

import com.billetplus.auth.api.dto.AgentDto;
import com.billetplus.auth.api.dto.CreateAgentRequest;
import com.billetplus.auth.api.dto.UpdateAgentRequest;
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

    /** Corrige le nom et l'e-mail d'un de SES agents. Inconnu, retiré ou d'un autre organisateur → 404. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<AgentDto> modifier(@PathVariable UUID id, @Valid @RequestBody UpdateAgentRequest request,
                                             Authentication auth) {
        UUID organisateurId = (UUID) auth.getPrincipal();
        User agent = agentAccountService.modifierAgent(organisateurId, id, request.email(), request.nomComplet());
        return ResponseEntity.ok(AgentDto.from(agent));
    }

    /** Retire un de SES agents (compte désactivé, affectations supprimées). Idempotent. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<Void> retirer(@PathVariable UUID id, Authentication auth) {
        agentAccountService.retirerAgent((UUID) auth.getPrincipal(), id);
        return ResponseEntity.noContent().build();
    }
}
