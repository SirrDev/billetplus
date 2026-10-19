package com.billetplus.events.api;

import com.billetplus.events.api.dto.CreateEventRequest;
import com.billetplus.events.api.dto.EventDto;
import com.billetplus.events.application.EventService;
import com.billetplus.events.domain.Event;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<EventDto> create(@Valid @RequestBody CreateEventRequest request, Authentication auth) {
        UUID organizerId = (UUID) auth.getPrincipal();
        Event event = eventService.create(
            request.titre(), request.description(), request.debuteLe(),
            request.termineLe(), request.venueId(), organizerId
        );
        return ResponseEntity.created(URI.create("/api/events/" + event.getId())).body(EventDto.from(event));
    }

    /**
     * Route publique (voir SecurityConfig) mais sensible à l'identité : un brouillon
     * n'est visible que par son organisateur, tous les autres reçoivent un 404.
     *
     * {@code viewerId} vaut null pour un visiteur non connecté : son principal est la
     * chaîne "anonymousUser", non assignable à UUID, donc le résolveur
     * d'@AuthenticationPrincipal renseigne null au lieu de lever une erreur.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventDto> get(@PathVariable UUID id, @AuthenticationPrincipal UUID viewerId) {
        return ResponseEntity.ok(EventDto.from(eventService.getVisible(id, viewerId)));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<List<EventDto>> mine(Authentication auth) {
        UUID organizerId = (UUID) auth.getPrincipal();
        List<EventDto> events = eventService.listMine(organizerId).stream().map(EventDto::from).toList();
        return ResponseEntity.ok(events);
    }

    @PostMapping("/{id}/publier")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<EventDto> publier(@PathVariable UUID id, Authentication auth) {
        UUID requestingUserId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(EventDto.from(eventService.publier(id, requestingUserId)));
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<EventDto> annuler(@PathVariable UUID id, Authentication auth) {
        UUID requestingUserId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(EventDto.from(eventService.annuler(id, requestingUserId)));
    }
}
