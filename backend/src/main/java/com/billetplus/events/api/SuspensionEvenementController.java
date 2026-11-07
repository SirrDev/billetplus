package com.billetplus.events.api;

import com.billetplus.events.api.dto.EventDto;
import com.billetplus.events.application.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Suspension d'un événement par l'administrateur (ADR 0012) : vente fermée, billets annulés,
 * commandes payées listées à rembourser. Motif obligatoire, lu par l'organisateur.
 */
@RestController
public class SuspensionEvenementController {

    public record SuspensionRequest(@Size(max = 500) String motif) {
    }

    private final EventService eventService;

    public SuspensionEvenementController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/api/admin/evenements/{id}/suspension")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventDto> suspendre(@PathVariable UUID id, @Valid @RequestBody SuspensionRequest request) {
        return ResponseEntity.ok(EventDto.from(eventService.suspendre(id, request.motif())));
    }
}
