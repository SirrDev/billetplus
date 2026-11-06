package com.billetplus.events.api;

import com.billetplus.events.application.VenueService;
import com.billetplus.events.domain.Pays;
import com.billetplus.events.domain.Ville;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Liste fermée des villes (ADR 0011), publique (SecurityConfig) : la PWA ne la recopie
 * jamais. Groupée par pays, dans l'ordre des pays puis de la liste. Elle ne change qu'avec
 * une migration : cache d'une heure.
 */
@RestController
public class VilleController {

    public record VilleDto(String code, String nom) {
    }

    public record PaysDto(String code, String nom, List<VilleDto> villes) {
    }

    private final VenueService venueService;

    public VilleController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping("/api/villes")
    public ResponseEntity<List<PaysDto>> villes() {
        Map<Pays, List<VilleDto>> parPays = new LinkedHashMap<>();
        for (Ville ville : venueService.villes()) {
            parPays.computeIfAbsent(ville.getPays(), p -> new ArrayList<>()).add(new VilleDto(ville.getCode(), ville.getNom()));
        }
        List<PaysDto> pays = parPays.entrySet().stream()
            .map(e -> new PaysDto(e.getKey().name(), e.getKey().getNom(), e.getValue()))
            .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(pays);
    }
}
