package com.billetplus.orders.api;

import com.billetplus.orders.api.dto.RecettesDto;
import com.billetplus.orders.api.dto.ReversementsDto;
import com.billetplus.orders.api.dto.EnregistrerReversementRequest;
import com.billetplus.orders.application.usecase.ConsulterRecettesUseCase;
import com.billetplus.orders.application.usecase.EnregistrerReversementUseCase;
import com.billetplus.orders.application.usecase.ListerReversementsUseCase;
import com.billetplus.orders.domain.Reversement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Commission de Billet+ et reversements aux organisateurs (ADR 0009).
 * - recettes d'un événement : son organisateur seulement (jeton exigé aussi par SecurityConfig) ;
 * - reversements : l'administrateur seulement.
 */
@RestController
public class RecettesController {

    private final ConsulterRecettesUseCase consulterRecettes;
    private final ListerReversementsUseCase listerReversements;
    private final EnregistrerReversementUseCase enregistrerReversement;

    public RecettesController(ConsulterRecettesUseCase consulterRecettes, ListerReversementsUseCase listerReversements,
                              EnregistrerReversementUseCase enregistrerReversement) {
        this.consulterRecettes = consulterRecettes;
        this.listerReversements = listerReversements;
        this.enregistrerReversement = enregistrerReversement;
    }

    @GetMapping("/api/events/{id}/recettes")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<RecettesDto> recettes(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(RecettesDto.from(consulterRecettes.execute(id, (UUID) auth.getPrincipal())));
    }

    @GetMapping("/api/admin/reversements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReversementsDto.Ligne>> reversements() {
        return ResponseEntity.ok(listerReversements.execute().stream().map(ReversementsDto.Ligne::from).toList());
    }

    @PostMapping("/api/admin/reversements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReversementsDto.Enregistre> enregistrer(@Valid @RequestBody EnregistrerReversementRequest request,
                                                                  Authentication auth) {
        Reversement reversement = enregistrerReversement.execute(request.eventId(), request.montantFcfa(),
            request.reference(), request.motif(), request.reverseLe(), (UUID) auth.getPrincipal(),
            request.derogationScans());
        return ResponseEntity.created(URI.create("/api/admin/reversements/" + reversement.getId()))
            .body(ReversementsDto.Enregistre.from(reversement));
    }
}
