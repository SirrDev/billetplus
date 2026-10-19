package com.billetplus.events.api;

import com.billetplus.events.api.dto.CreateVenueRequest;
import com.billetplus.events.api.dto.VenueDto;
import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Venue;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISATEUR') or hasRole('ADMIN')")
    public ResponseEntity<VenueDto> create(@Valid @RequestBody CreateVenueRequest request) {
        Venue venue = venueService.create(request.nom(), request.adresse(), request.latitude(), request.longitude());
        return ResponseEntity.created(URI.create("/api/venues/" + venue.getId())).body(VenueDto.from(venue));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VenueDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(VenueDto.from(venueService.get(id)));
    }
}
